package com.training.orderservice.dto.request;

import com.training.orderservice.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateOrderStatusRequest {
    @NotNull(message = "status is required")
    private OrderStatus status;

    public UpdateOrderStatusRequest() {}
    public UpdateOrderStatusRequest(OrderStatus status) { this.status = status; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
}
