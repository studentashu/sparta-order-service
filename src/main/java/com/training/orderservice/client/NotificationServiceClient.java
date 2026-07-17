package com.training.orderservice.client;

import com.training.orderservice.client.dto.OrderConfirmationRequest;

public interface NotificationServiceClient {

    void sendOrderConfirmation(OrderConfirmationRequest request);
}
