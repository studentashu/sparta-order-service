package com.training.orderservice.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderConfirmationRequest(
        UUID orderId,
        Long customerId,
        String customerName,
        String customerEmail,
        BigDecimal totalAmount,
        List<Item> items,
        LocalDateTime confirmedAt) {

    public record Item(String productName, Integer quantity) {
    }
}
