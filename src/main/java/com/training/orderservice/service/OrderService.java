package com.training.orderservice.service;

import com.training.orderservice.dto.request.CreateOrderRequest;
import com.training.orderservice.dto.request.UpdateOrderStatusRequest;
import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request);

    Page<OrderResponse> getOrders(OrderStatus status, Long customerId, Pageable pageable);

    OrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request);
}
