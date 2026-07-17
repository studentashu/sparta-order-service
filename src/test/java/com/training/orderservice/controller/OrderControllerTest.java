package com.training.orderservice.controller;

import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.exception.OrderNotFoundException;
import com.training.orderservice.mapper.OrderMapper;
import com.training.orderservice.security.CallerContext;
import com.training.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderMapper orderMapper;

    @Test
    void returns200WithOrderWhenFound() throws Exception {
        Order order = new Order(101L, "Jane Doe", "jane@example.com", "221B Baker Street");
        when(orderService.getOrderById(eq(1001L), any(CallerContext.class))).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(new OrderResponse(
                1001L, 101L, "Jane Doe", "jane@example.com", "221B Baker Street",
                OrderStatus.CONFIRMED, new BigDecimal("50.00"), List.of(), null, null));

        mockMvc.perform(get("/api/v1/orders/1001").header("X-Customer-Id", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1001))
                .andExpect(jsonPath("$.customerId").value(101));
    }

    @Test
    void returns404WhenOrderNotFoundOrNotOwned() throws Exception {
        when(orderService.getOrderById(eq(1001L), any(CallerContext.class)))
                .thenThrow(new OrderNotFoundException(1001L));

        mockMvc.perform(get("/api/v1/orders/1001").header("X-Customer-Id", "202"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
    }

    @Test
    void returns400WhenCustomerIdHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/v1/orders/1001"))
                .andExpect(status().isBadRequest());
    }
}
