package com.training.orderservice.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderConfirmationRequest(
        Long orderId,
        Long customerId,
        String customerName,
        String customerEmail,
        BigDecimal totalAmount,
        List<Item> items,
        LocalDateTime confirmedAt) {

    public record Item(String productName, Integer quantity) {
    }
}
