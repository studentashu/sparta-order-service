package com.training.orderservice.client.impl;

import com.training.orderservice.client.ProductServiceClient;
import com.training.orderservice.client.dto.ProductSnapshot;
import com.training.orderservice.exception.InsufficientStockException;
import com.training.orderservice.exception.ProductNotFoundException;
import com.training.orderservice.exception.ProductServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import com.training.orderservice.exception.ProductServiceUnavailableException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Talks to the real Product Service over HTTP (SDD Section 29.1). The endpoint
 * shapes below follow the Product Service's actual contract, not the SDD's
 * originally-assumed one — only this implementation needed to change, per the
 * SDD's own note that the Order Service's domain logic stays untouched either way.
 * Static stub — Product Service isn't integrated yet (owned by another team/module).
 * Every product is reported as existing with ample stock so the order-creation flow
 * can be exercised end-to-end; replace with a real client per Section 29.1 later.
 * The circuit breaker + bulkhead wrapping (Section 8/14) is in place ahead of that.
 */
@Component
public class ProductServiceRestClient implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceRestClient.class);

    private final RestClient restClient;

    public ProductServiceRestClient(@Qualifier("productServiceHttpClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProductSnapshot getProduct(UUID productId) {
        try {
            ProductResponse response = restClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .body(ProductResponse.class);
            return new ProductSnapshot(response.id(), response.productName(), response.price(), response.stockQuantity());
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException("Product " + productId + " was not found in the Product Service");
        } catch (RestClientException ex) {
            log.warn("Product Service call failed while fetching product {}", productId, ex);
            throw new ProductServiceUnavailableException("Product Service is unavailable while fetching product " + productId, ex);
        }
    }

    @Override
    public void reduceStock(UUID productId, int quantity, Long orderId) {
        try {
            restClient.patch()
                    .uri("/api/v1/products/{id}/reduce-stock", productId)
                    .body(new StockReductionRequest(quantity, String.valueOf(orderId)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException("Product " + productId + " was not found in the Product Service");
        } catch (HttpClientErrorException.Conflict ex) {
            // Product Service's own atomic reduce-stock enforces stock non-negativity as a
            // race-condition safety net beyond the Order Service's own availability check.
            throw new InsufficientStockException(
                    "Product Service rejected the stock reduction for product " + productId + ": insufficient stock");
        } catch (RestClientException ex) {
            log.warn("Product Service call failed while reducing stock for product {}", productId, ex);
            throw new ProductServiceUnavailableException("Product Service is unavailable while reducing stock for product " + productId, ex);
        }
    }

    @Override
    public void restoreStock(UUID productId, int quantity, Long orderId) {
        try {
            restClient.patch()
                    .uri("/api/v1/products/{id}/stock", productId)
                    .body(new StockAdjustmentRequest(quantity, "INCREASE"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException("Product " + productId + " was not found in the Product Service");
        } catch (RestClientException ex) {
            log.warn("Product Service call failed while restoring stock for product {}", productId, ex);
            throw new ProductServiceUnavailableException("Product Service is unavailable while restoring stock for product " + productId, ex);
        }
    }

    private record ProductResponse(UUID id, String productName, BigDecimal price, Integer stockQuantity) {
    }

    private record StockReductionRequest(Integer quantity, String orderReference) {
    }

    private record StockAdjustmentRequest(Integer quantity, String operation) {
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
