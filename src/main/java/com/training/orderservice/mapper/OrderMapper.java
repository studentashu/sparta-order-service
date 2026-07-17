package com.training.orderservice.mapper;

import com.training.orderservice.dto.response.OrderItemResponse;
import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems().stream().map(this::toItemResponse).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getUnitPriceSnapshot(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }
}
