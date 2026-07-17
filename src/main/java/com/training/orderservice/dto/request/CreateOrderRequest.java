package com.training.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull
    @Positive
    private Long customerId;

    @NotBlank
    @Size(max = 150)
    private String customerName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String customerEmail;

    @NotBlank
    @Size(max = 500)
    private String shippingAddress;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;
}
