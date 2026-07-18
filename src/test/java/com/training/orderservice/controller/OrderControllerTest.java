package com.training.orderservice.controller;

import com.training.orderservice.dto.response.OrderResponse;
import com.training.orderservice.entity.Order;
import com.training.orderservice.entity.OrderStatus;
import com.training.orderservice.exception.InvalidOrderStatusTransitionException;
import com.training.orderservice.exception.OrderAccessDeniedException;
import com.training.orderservice.exception.OrderNotFoundException;
import com.training.orderservice.mapper.OrderMapper;
import com.training.orderservice.security.CallerContext;
import com.training.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void create_returns201WithCreatedOrder() throws Exception {
        OrderResponse response = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CONFIRMED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderService.createOrder(any())).thenReturn(response);

        String body = "{\"customerId\":101,\"customerName\":\"Jane Doe\",\"customerEmail\":\"jane@example.com\","
                + "\"shippingAddress\":\"221B Baker Street\",\"items\":[{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":2}]}";

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1001))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void create_returns400WhenRequestInvalid() throws Exception {
        // empty body violates @NotNull / @NotBlank / @NotEmpty constraints
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void list_returns200WithPageOfOrders() throws Exception {
        OrderResponse response = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CONFIRMED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderService.getOrders(any(), any(), any())).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(1001));
    }

    @Test
    void updateStatus_returns200WithUpdatedOrder() throws Exception {
        OrderResponse response = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.SHIPPED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderService.updateStatus(eq(1001L), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/orders/1001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void updateStatus_returns409OnInvalidTransition() throws Exception {
        when(orderService.updateStatus(eq(1001L), any()))
                .thenThrow(new InvalidOrderStatusTransitionException(OrderStatus.DELIVERED, OrderStatus.PENDING));

        mockMvc.perform(patch("/api/v1/orders/1001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS_TRANSITION"));
    }

    @Test
    void cancel_returns200WhenCancellable() throws Exception {
        OrderResponse response = new OrderResponse(1001L, 101L, "Jane Doe", "jane@example.com",
                "221B Baker Street", OrderStatus.CANCELLED, new BigDecimal("50.00"), List.of(), null, null);
        when(orderService.cancelOrder(eq(1001L), any(CallerContext.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/1001/cancel").header("X-Customer-Id", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_returns409WhenNotCancellable() throws Exception {
        when(orderService.cancelOrder(eq(1001L), any(CallerContext.class)))
                .thenThrow(new InvalidOrderStatusTransitionException(OrderStatus.DELIVERED, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/orders/1001/cancel").header("X-Customer-Id", "101"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS_TRANSITION"));
    }

    @Test
    void cancel_returns404WhenMissingOrNotOwned() throws Exception {
        when(orderService.cancelOrder(eq(1001L), any(CallerContext.class)))
                .thenThrow(new OrderNotFoundException(1001L));

        mockMvc.perform(post("/api/v1/orders/1001/cancel").header("X-Customer-Id", "202"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
    }

    @Test
    void delete_returns204WhenAdminDeletes() throws Exception {
        // service mock does nothing (void) → successful delete
        mockMvc.perform(delete("/api/v1/orders/1001").header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns403WhenCallerNotAdmin() throws Exception {
        doThrow(new OrderAccessDeniedException("Hard-delete of an order requires an admin role."))
                .when(orderService).deleteOrder(eq(1001L), any(CallerContext.class));

        mockMvc.perform(delete("/api/v1/orders/1001").header("X-User-Role", "CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ORDER_ACCESS_DENIED"));
    }

    @Test
    void delete_returns404WhenOrderNotFound() throws Exception {
        doThrow(new OrderNotFoundException(1001L))
                .when(orderService).deleteOrder(eq(1001L), any(CallerContext.class));

        mockMvc.perform(delete("/api/v1/orders/1001").header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
    }
}
