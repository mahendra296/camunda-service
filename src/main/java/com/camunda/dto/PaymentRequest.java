package com.camunda.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequest {

    /** Unique payment identifier (caller-supplied idempotency key). */
    @NotBlank(message = "paymentId is required")
    private String paymentId;

    /** Customer identifier. */
    @NotBlank(message = "customerId is required")
    private String customerId;

    /**
     * Payment amount.
     * Set amount > 10,000 to simulate INSUFFICIENT_FUNDS and trigger the error boundary.
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private Double amount;

    /** ISO 4217 currency code (e.g. USD, KES). */
    @NotBlank(message = "currency is required")
    private String currency;

    /**
     * Payment method — pass "INVALID" to simulate an INVALID_PAYMENT_METHOD error.
     * Accepted values: CARD, MOBILE_MONEY, BANK_TRANSFER, INVALID
     */
    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;
}
