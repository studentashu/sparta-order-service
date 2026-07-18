package com.training.orderservice.client;

import com.training.orderservice.client.dto.ProductSnapshot;

import java.util.UUID;

public interface ProductServiceClient {

    ProductSnapshot getProduct(UUID productId);

    void reduceStock(UUID productId, int quantity, Long orderId);

    void restoreStock(UUID productId, int quantity, Long orderId);
}
