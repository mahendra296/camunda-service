package com.camunda.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoanDocumentProcessResponse {

    private String documentId;
    private long processInstanceKey;
    private String status;
    private String message;
}
