package com.camunda.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ValidateApplicationResponse {

    private final boolean applicationValid;
    private final String validationError;
    private final long validatedAt;
    private final int creditScore;
    private final int applicantAge;
    private final double monthlyIncome;
    private final double requestedAmount;
    private final Map<String, Object> riskConfig;
}
