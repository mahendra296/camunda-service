package com.camunda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AirtelLoanSubmitRequest {

    /** MSISDN used as correlation key to route the submission to the waiting process instance. */
    @NotBlank(message = "msisdn is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "msisdn must be 10-15 digits")
    private String msisdn;

    /** Optional: override loan amount submitted by customer. */
    private Double loanAmount;

    /** Optional: override loan tenure in months. */
    private Integer tenureMonths;

    /** Optional: override loan purpose (e.g. PERSONAL, BUSINESS). */
    private String loanPurpose;
}
