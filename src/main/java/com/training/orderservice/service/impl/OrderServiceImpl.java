package com.training.orderservice.service.impl;

import com.training.orderservice.client.NotificationServiceClient;
import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.client.dto.OrderConfirmationRequest;
import com.training.orderservice.client.dto.ProductSnapshot;
import com.training.orderservice.dto.request.CreateOrderRequest;
import com.training.orderservice.dto.request.OrderItemRequest;
import com.training.orderservice.dto.request.UpdateOrderStatusRequest;
import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.entity.OrderReconciliationLog;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.exception.DuplicateProductInOrderException;
import com.training.orderservice.exception.InsufficientStockException;
import com.training.orderservice.exception.InvalidOrderStatusTransitionException;
import com.training.orderservice.exception.OrderAccessDeniedException;
import com.training.orderservice.exception.OrderNotFoundException;
import com.training.orderservice.mapper.OrderMapper;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.repository.OrderReconciliationLogRepository;
import com.training.orderservice.security.CallerContext;
import com.training.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final OrderReconciliationLogRepository reconciliationLogRepository;
    private final ProductServiceClient productServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository,
                             OrderReconciliationLogRepository reconciliationLogRepository,
                             ProductServiceClient productServiceClient,
                             NotificationServiceClient notificationServiceClient,
                             OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.productServiceClient = productServiceClient;
        this.notificationServiceClient = notificationServiceClient;
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        validateNoDuplicateProducts(request);

        Order order = new Order(request.getCustomerId(), request.getCustomerName(),
                request.getCustomerEmail(), request.getShippingAddress());
        order = orderRepository.save(order);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductSnapshot snapshot = productServiceClient.getProduct(itemRequest.getProductId());
            if (snapshot.stockQuantity() < itemRequest.getQuantity()) {
                order.setStatus(OrderStatus.REJECTED);
                orderRepository.save(order);
                throw new InsufficientStockException("Product " + itemRequest.getProductId() + " has only "
                        + snapshot.stockQuantity() + " units available, requested " + itemRequest.getQuantity());
            }

            productServiceClient.reduceStock(itemRequest.getProductId(), itemRequest.getQuantity(), order.getId());

            BigDecimal subtotal = snapshot.price().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            order.addItem(new OrderItem(order, itemRequest.getProductId(), snapshot.name(),
                    snapshot.price(), itemRequest.getQuantity()));
            total = total.add(subtotal);
        }

        order.setTotalAmount(total);
        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.save(order);

        dispatchConfirmationNotification(order);

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId, CallerContext caller) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!caller.isAdmin() && !order.getCustomerId().equals(caller.customerId())) {
            // BR-10: a cross-customer access attempt must not be distinguishable from a
            // missing order, so this reuses OrderNotFoundException rather than a 403.
            throw new OrderNotFoundException(orderId);
        }

        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderStatus status, Long customerId, Pageable pageable) {
        return orderRepository.findByOptionalFilters(status, customerId, pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Status must not be null");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus from = order.getStatus();
        OrderStatus to = request.getStatus();

        // No-op if the status is unchanged.
        if (from == to) {
            log.info("Order {} already in status {}", orderId, from);
            return orderMapper.toResponse(order);
        }

        // Enforce the state-machine (SDD Section 11).
        if (!from.canManuallyTransitionTo(to)) {
            log.warn("Invalid status transition for order {}: {} -> {}", orderId, from, to);
            throw new InvalidOrderStatusTransitionException(from, to);
        }

        order.setStatus(to);
        Order saved = orderRepository.save(order);
        log.info("Order {} transitioned {} -> {}", orderId, from, to);

        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long orderId, CallerContext caller) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // BR-10: a customer may only cancel their own order; a cross-customer attempt is
        // indistinguishable from a missing order (consistent with getOrderById).
        if (!caller.isAdmin() && !order.getCustomerId().equals(caller.customerId())) {
            throw new OrderNotFoundException(orderId);
        }

        OrderStatus from = order.getStatus();

        // BR-5 / BR-7: only PENDING or CONFIRMED orders can be cancelled.
        if (!from.canManuallyTransitionTo(OrderStatus.CANCELLED)) {
            throw new InvalidOrderStatusTransitionException(from, OrderStatus.CANCELLED);
        }

        // BR-6: a CONFIRMED order already had its stock reduced, so restore it (best-effort).
        // A failure here must not abort the cancellation itself (that would leave the customer's
        // cancel request lost as well as the stock inconsistent) — it's recorded instead so the
        // reconciliation sweep (Section 31) can retry the restore later.
        if (from == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                try {
                    productServiceClient.restoreStock(item.getProductId(), item.getQuantity(), order.getId());
                } catch (Exception ex) {
                    log.warn("Stock restore failed for order {} product {} (will be retried by the reconciliation sweep): {}",
                            orderId, item.getProductId(), ex.getMessage());
                    reconciliationLogRepository.save(OrderReconciliationLog.forRestoreFailure(
                            order.getId(), item.getProductId(), item.getQuantity()));
                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        log.info("Order {} cancelled (was {})", orderId, from);

        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteOrder(Long orderId, CallerContext caller) {
        // BR-11: hard-delete is admin-only. Check the role before the lookup so a
        // non-admin cannot probe for the existence of an order (403, not 404).
        if (!caller.isAdmin()) {
            throw new OrderAccessDeniedException("Hard-delete of an order requires an admin role.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Hard delete: physically remove the order (and cascaded order_items).
        orderRepository.delete(order);
        log.info("Order {} hard-deleted by admin", orderId);
    }

    private void validateNoDuplicateProducts(CreateOrderRequest request) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest item : request.getItems()) {
            if (!seen.add(item.getProductId())) {
                throw new DuplicateProductInOrderException(
                        "Duplicate productId " + item.getProductId() + " in request items");
            }
        }
    }

    private void dispatchConfirmationNotification(Order order) {
        var items = order.getItems().stream()
                .map(i -> new OrderConfirmationRequest.Item(i.getProductNameSnapshot(), i.getQuantity()))
                .toList();
        OrderConfirmationRequest payload = new OrderConfirmationRequest(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                items,
                LocalDateTime.now());
        notificationServiceClient.sendOrderConfirmation(payload);
    }
}
