package com.camunda.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class OrderRequest {

    @NotBlank
    private String orderId;

    @NotBlank
    private String customerId;

    @Email
    @NotBlank
    private String customerEmail;

    @NotBlank
    private String customerName;

    @NotEmpty
    @Valid
    private List<OrderItemDto> items;

    @NotBlank
    private String shippingAddress;

    private String paymentMethod; // CREDIT_CARD, DEBIT_CARD, NET_BANKING
    private String paymentToken;
}
