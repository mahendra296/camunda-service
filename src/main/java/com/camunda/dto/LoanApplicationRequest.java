package com.camunda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class LoanApplicationRequest {

    /** Unique application identifier — used as correlation key. */
    @NotBlank(message = "applicationId is required")
    private String applicationId;

    /** Full name of the loan applicant. */
    @NotBlank(message = "applicantName is required")
    private String applicantName;

    /** Age of the applicant in years — must be at least 18. */
    @Min(value = 18, message = "applicant must be at least 18 years old")
    private int applicantAge;

    /** Gross monthly income in local currency units. */
    @Positive(message = "monthlyIncome must be positive")
    private double monthlyIncome;

    /** Requested loan amount in local currency units. */
    @Positive(message = "requestedAmount must be positive")
    private double requestedAmount;

    /**
     * Optional credit score (300–850). When omitted, the risk worker
     * simulates a score based on the applicant profile.
     */
    @Min(value = 300, message = "creditScore must be between 300 and 850")
    @Max(value = 850, message = "creditScore must be between 300 and 850")
    private Integer creditScore;
}
