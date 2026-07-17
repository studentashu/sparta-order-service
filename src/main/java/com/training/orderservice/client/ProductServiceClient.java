package com.training.orderservice.client;

import com.training.orderservice.client.dto.ProductSnapshot;

import java.util.UUID;

public interface ProductServiceClient {

    ProductSnapshot getProduct(Long productId);

    void reduceStock(Long productId, int quantity, UUID orderId);
}
