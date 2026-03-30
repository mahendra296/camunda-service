package com.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoanAssessmentResponse {

    private String applicationId;
    private long processInstanceKey;
    private String status;
    private String message;
}
