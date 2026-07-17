package com.training.orderservice.client.impl;

import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.client.dto.ProductSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Static stub — Product Service isn't integrated yet (owned by another team/module).
 * Every product is reported as existing with ample stock so the order-creation flow
 * can be exercised end-to-end; replace with a real client per Section 29.1 later.
 */
@Component
public class ProductServiceRestClient implements ProductServiceClient {

    @Override
    public ProductSnapshot getProduct(Long productId) {
        return new ProductSnapshot(productId, "Product " + productId, new BigDecimal("9.99"), 1000);
    }

    @Override
    public void reduceStock(Long productId, int quantity, UUID orderId) {
        // no-op until Product Service is integrated
    }
}
