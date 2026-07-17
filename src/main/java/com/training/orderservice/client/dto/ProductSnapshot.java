package com.training.orderservice.client.dto;

import java.math.BigDecimal;

public record ProductSnapshot(Long productId, String name, BigDecimal price, Integer stockQuantity) {
}
