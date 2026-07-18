package com.training.orderservice.client.impl;

import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.client.dto.ProductSnapshot;
import com.training.orderservice.exception.ProductServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Static stub — Product Service isn't integrated yet (owned by another team/module).
 * Every product is reported as existing with ample stock so the order-creation flow
 * can be exercised end-to-end; replace with a real client per Section 29.1 later.
 * The circuit breaker + bulkhead wrapping (Section 8/14) is in place ahead of that.
 */
@Component
public class ProductServiceRestClient implements ProductServiceClient {

    private static final String PRODUCT_SERVICE = "productService";

    @Override
    @CircuitBreaker(name = PRODUCT_SERVICE, fallbackMethod = "getProductFallback")
    @Bulkhead(name = PRODUCT_SERVICE, fallbackMethod = "getProductFallback")
    public ProductSnapshot getProduct(Long productId) {
        return new ProductSnapshot(productId, "Product " + productId, new BigDecimal("9.99"), 1000);
    }

    @Override
    @CircuitBreaker(name = PRODUCT_SERVICE, fallbackMethod = "reduceStockFallback")
    @Bulkhead(name = PRODUCT_SERVICE, fallbackMethod = "reduceStockFallback")
    public void reduceStock(Long productId, int quantity, Long orderId) {
        // no-op until Product Service is integrated
    }

    @Override
    @CircuitBreaker(name = PRODUCT_SERVICE, fallbackMethod = "restoreStockFallback")
    @Bulkhead(name = PRODUCT_SERVICE, fallbackMethod = "restoreStockFallback")
    public void restoreStock(Long productId, int quantity, Long orderId) {
        // no-op until Product Service is integrated (compensating call for cancellation, BR-6)
    }

    private ProductSnapshot getProductFallback(Long productId, Throwable t) {
        throw new ProductServiceUnavailableException("Product Service unavailable while fetching product " + productId, t);
    }

    private void reduceStockFallback(Long productId, int quantity, Long orderId, Throwable t) {
        throw new ProductServiceUnavailableException("Product Service unavailable while reducing stock for product " + productId, t);
    }

    private void restoreStockFallback(Long productId, int quantity, Long orderId, Throwable t) {
        throw new ProductServiceUnavailableException("Product Service unavailable while restoring stock for product " + productId, t);
    }
}
