package com.training.orderservice.service;

import com.training.orderservice.dto.request.CreateOrderRequest;
import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.security.CallerContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    Order getOrderById(Long orderId, CallerContext caller);

    OrderResponse createOrder(CreateOrderRequest request);

    Page<OrderResponse> getOrders(OrderStatus status, Long customerId, Pageable pageable);
}
