package com.training.orderservice.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID productId, String name, BigDecimal price, Integer stockQuantity) {
}
