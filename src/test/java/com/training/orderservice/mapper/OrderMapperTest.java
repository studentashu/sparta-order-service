package com.training.orderservice.mapper;

import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    void mapsOrderAndItemsToResponse() {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        order.setTotalAmount(new BigDecimal("50.00"));
        OrderItem item = new OrderItem(order, 55L, "Wireless Mouse", new BigDecimal("25.00"), 2);
        order.getItems().add(item);

        OrderResponse response = mapper.toResponse(order);

        assertThat(response.customerId()).isEqualTo(101L);
        assertThat(response.customerName()).isEqualTo("Jane Doe");
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(55L);
        assertThat(response.items().get(0).productName()).isEqualTo("Wireless Mouse");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
    }
}
