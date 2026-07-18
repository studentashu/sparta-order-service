package com.training.orderservice.client.impl;

import com.training.orderservice.client.NotificationServiceClient;
import com.training.orderservice.client.dto.OrderConfirmationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Static stub — Notification Service isn't integrated yet (owned by another team/module).
 * Replace with a real HTTP call per Section 29.2 later; the async dispatch (Section 15) is in place.
 */
@Component
public class NotificationServiceRestClient implements NotificationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceRestClient.class);

    @Override
    @Async("notification-executor")
    public void sendOrderConfirmation(OrderConfirmationRequest request) {
        log.info("Notification stub: would send order-confirmation for order {}", request.orderId());
    }
}
