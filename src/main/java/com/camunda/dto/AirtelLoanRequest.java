package com.camunda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AirtelLoanRequest {

    /** MSISDN (mobile number) — primary correlation key throughout the BPMN flow. */
    @NotBlank(message = "msisdn is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "msisdn must be 10-15 digits")
    private String msisdn;

    /** Requested loan amount in local currency units. */
    private double loanAmount;

    /** Loan tenure in months. */
    private int tenureMonths;

    /** Optional: purpose of the loan (e.g., PERSONAL, BUSINESS). */
    private String loanPurpose;

    /**
     * Optional: override whether the customer already exists in CapBPM.
     * When provided, the CheckCustomerExists worker uses this value directly
     * instead of performing the lookup.
     */
    private Boolean customerExists;
}
