package com.training.orderservice.service;

import com.training.orderservice.client.NotificationServiceClient;
import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.entity.Order;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

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
}
