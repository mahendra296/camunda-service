package com.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResponse {

    private String paymentId;
    private long processInstanceKey;
    private String status;
    private String message;
}
