package com.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StartDemoResponse {

    private final long processInstanceKey;
    private final String status;
    private final String message;
}
