package com.camunda.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExtractedLoanDataResponse {

    private final String borrowerName;
    private final String loanNumber;
    private final String propertyAddress;
    private final double loanAmount;
    private final String closingDate;
    private final double confidence;
    private final long extractedAt;
}
