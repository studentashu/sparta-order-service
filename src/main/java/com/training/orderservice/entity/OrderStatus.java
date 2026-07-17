package com.training.orderservice.entity;

public enum OrderStatus {
    PENDING,      // Order created, waiting for verification
    CONFIRMED,    // Stock verified, ready to ship
    REJECTED,     // Product not found or out of stock
    CANCELLED,    // Customer cancelled the order
    SHIPPED,      // Order shipped to customer
    DELIVERED;     // DELIVERED TO CUSTOMER

    public boolean canManuallyTransitionTo(OrderStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case REJECTED, CANCELLED, DELIVERED -> false;
        };
    }
}
