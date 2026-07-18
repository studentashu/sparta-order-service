package com.training.orderservice.service;

import com.training.orderservice.client.NotificationServiceClient;
import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.client.dto.ProductSnapshot;
import com.training.orderservice.dto.request.CreateOrderRequest;
import com.training.orderservice.dto.request.OrderItemRequest;
import com.training.orderservice.dto.request.UpdateOrderStatusRequest;
import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.exception.DuplicateProductInOrderException;
import com.training.orderservice.exception.InsufficientStockException;
import com.training.orderservice.exception.InvalidOrderStatusTransitionException;
import com.training.orderservice.exception.OrderAccessDeniedException;
import com.training.orderservice.exception.OrderNotFoundException;
import com.training.orderservice.mapper.OrderMapper;
import com.training.orderservice.repository.OrderRepository;
import com.training.orderservice.security.CallerContext;
import com.training.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Mock
    private OrderMapper orderMapper;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, productServiceClient, notificationServiceClient, orderMapper);
    }

    @Test
    void returnsOrderWhenCallerIsOwner() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(1001L, new CallerContext(101L, "CUSTOMER"));

        assertThat(result).isSameAs(order);
    }

    @Test
    void returnsOrderWhenCallerIsAdminEvenIfNotOwner() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderById(1001L, new CallerContext(999L, "ADMIN"));

        assertThat(result).isSameAs(order);
    }

    @Test
    void throwsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(1001L, new CallerContext(101L, "CUSTOMER")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void throwsNotFoundRatherThanForbiddenWhenCallerDoesNotOwnOrder() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(1001L, new CallerContext(202L, "CUSTOMER")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ----- createOrder -----

    @Test
    void createOrder_confirmsAndReturnsResponseWhenStockAvailable() {
        CreateOrderRequest request = new CreateOrderRequest(101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", List.of(new OrderItemRequest(PRODUCT_ID, 2)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productServiceClient.getProduct(PRODUCT_ID))
                .thenReturn(new ProductSnapshot(PRODUCT_ID, "Wireless Mouse", new BigDecimal("25.00"), 10));
        OrderResponse mapped = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CONFIRMED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(mapped);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result).isSameAs(mapped);
        verify(productServiceClient).reduceStock(eq(PRODUCT_ID), eq(2), any());
        verify(notificationServiceClient).sendOrderConfirmation(any());
    }

    @Test
    void createOrder_throwsInsufficientStockWhenNotEnoughStock() {
        CreateOrderRequest request = new CreateOrderRequest(101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", List.of(new OrderItemRequest(PRODUCT_ID, 5)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productServiceClient.getProduct(PRODUCT_ID))
                .thenReturn(new ProductSnapshot(PRODUCT_ID, "Wireless Mouse", new BigDecimal("25.00"), 2));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class);
        verify(notificationServiceClient, never()).sendOrderConfirmation(any());
    }

    @Test
    void createOrder_throwsOnDuplicateProduct() {
        CreateOrderRequest request = new CreateOrderRequest(101L, "Jane Doe", "jane@example.com",
                "221B Baker Street",
                List.of(new OrderItemRequest(PRODUCT_ID, 1), new OrderItemRequest(PRODUCT_ID, 2)));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(DuplicateProductInOrderException.class);
        verify(orderRepository, never()).save(any());
    }

    // ----- getOrders -----

    @Test
    void getOrders_returnsMappedPage() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findByOptionalFilters(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(order)));
        OrderResponse mapped = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CONFIRMED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderMapper.toResponse(order)).thenReturn(mapped);

        Page<OrderResponse> result = orderService.getOrders(null, null, pageable);

        assertThat(result.getContent()).containsExactly(mapped);
    }

    // ----- updateStatus -----

    @Test
    void updateStatus_transitionsAndReturnsResponse() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street"); // PENDING
        when(orderRepository.findById(1001L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderResponse mapped = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CONFIRMED, new BigDecimal("0.00"), List.of(), null, null);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(mapped);

        OrderResponse result = orderService.updateStatus(1001L, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED));

        assertThat(result).isSameAs(mapped);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void updateStatus_throwsOnInvalidTransition() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1001L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(1001L, new UpdateOrderStatusRequest(OrderStatus.PENDING)))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void updateStatus_throwsWhenOrderNotFound() {
        when(orderRepository.findById(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(1001L, new UpdateOrderStatusRequest(OrderStatus.CONFIRMED)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ----- cancelOrder -----

    @Test
    void cancelOrder_cancelsPendingWithoutStockRestore() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street"); // PENDING
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderResponse mapped = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CANCELLED, new BigDecimal("0.00"), List.of(), null, null);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(mapped);

        OrderResponse result = orderService.cancelOrder(1001L, new CallerContext(101L, "CUSTOMER"));

        assertThat(result).isSameAs(mapped);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productServiceClient, never()).restoreStock(any(), anyInt(), any());
    }

    @Test
    void cancelOrder_cancelsConfirmedAndRestoresStock() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        order.setStatus(OrderStatus.CONFIRMED);
        order.addItem(new OrderItem(order, PRODUCT_ID, "Wireless Mouse", new BigDecimal("25.00"), 2));
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        OrderResponse mapped = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CANCELLED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderMapper.toResponse(any(Order.class))).thenReturn(mapped);

        OrderResponse result = orderService.cancelOrder(1001L, new CallerContext(101L, "CUSTOMER"));

        assertThat(result).isSameAs(mapped);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productServiceClient).restoreStock(eq(PRODUCT_ID), eq(2), any());
    }

    @Test
    void cancelOrder_throwsWhenNotCancellable() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1001L, new CallerContext(101L, "CUSTOMER")))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    void cancelOrder_throwsNotFoundWhenMissing() {
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(1001L, new CallerContext(101L, "CUSTOMER")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelOrder_throwsNotFoundWhenCrossCustomer() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        when(orderRepository.findByIdWithItems(1001L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1001L, new CallerContext(202L, "CUSTOMER")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ----- deleteOrder -----

    @Test
    void deleteOrder_deletesWhenCallerIsAdmin() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        when(orderRepository.findById(1001L)).thenReturn(Optional.of(order));

        orderService.deleteOrder(1001L, new CallerContext(999L, "ADMIN"));

        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_throwsForbiddenWhenCallerNotAdmin() {
        assertThatThrownBy(() -> orderService.deleteOrder(1001L, new CallerContext(101L, "CUSTOMER")))
                .isInstanceOf(OrderAccessDeniedException.class);
        verify(orderRepository, never()).delete(any());
    }

    @Test
    void deleteOrder_throwsNotFoundWhenMissing() {
        when(orderRepository.findById(1001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(1001L, new CallerContext(999L, "ADMIN")))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
