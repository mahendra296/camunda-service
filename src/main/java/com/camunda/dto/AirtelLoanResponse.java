package com.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AirtelLoanResponse {

    private String msisdn;
    private long instanceKey;
    private String status;
    private String message;
}
