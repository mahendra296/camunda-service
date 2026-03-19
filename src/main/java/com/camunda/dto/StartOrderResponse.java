package com.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StartOrderResponse {

    private String orderId;
    private long processInstanceKey;
    private String status;
    private String message;
}
