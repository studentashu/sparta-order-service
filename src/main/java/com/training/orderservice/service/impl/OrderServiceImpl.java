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
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.exception.*;
import com.training.orderservice.mapper.OrderMapper;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository,
                             ProductServiceClient productServiceClient,
                             NotificationServiceClient notificationServiceClient,
                             OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
        this.notificationServiceClient = notificationServiceClient;
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        validateNoDuplicateProducts(request);

        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setShippingAddress(request.getShippingAddress());
        order.setStatus(OrderStatus.PENDING);
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
            OrderItem item = new OrderItem();
            item.setProductId(itemRequest.getProductId());
            item.setProductNameSnapshot(snapshot.name());
            item.setUnitPriceSnapshot(snapshot.price());
            item.setQuantity(itemRequest.getQuantity());
            item.setSubtotal(subtotal);
            order.addItem(item);
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
    public Page<OrderResponse> getOrders(OrderStatus status, Long customerId, Pageable pageable) {
        return orderRepository.findByOptionalFilters(status, customerId, pageable)
                .map(orderMapper::toResponse);
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

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request) {

        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("Status must not be null");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus from = order.getStatus();
        OrderStatus to = request.getStatus();

        // Avoid unnecessary update
        if (from == to) {
            log.info("Order {} already in status {}", orderId, from);
            return orderMapper.toResponse(order);
        }

        // Validate transition
        if (!from.canManuallyTransitionTo(to)) {
            log.warn("Invalid status transition for order {}: {} -> {}", orderId, from, to);
            throw new InvalidOrderStatusTransitionException(from, to);
        }

        order.setStatus(to);

        Order saved = orderRepository.save(order);

        log.info("Order {} transitioned {} -> {}", orderId, from, to);

        return orderMapper.toResponse(saved);
    }
}
