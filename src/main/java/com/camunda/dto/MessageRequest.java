package com.camunda.dto;

import java.util.Map;
import lombok.Data;

@Data
public class MessageRequest {

    private String orderId; // correlation key
    private Map<String, Object> variables;
}
