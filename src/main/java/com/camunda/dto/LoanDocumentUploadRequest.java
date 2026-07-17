package com.camunda.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoanDocumentUploadRequest {

    /** Unique identifier for the uploaded document. */
    @NotBlank(message = "documentId is required")
    private String documentId;

    /** Loan number this document belongs to. */
    @NotBlank(message = "loanNumber is required")
    private String loanNumber;

    /**
     * Absolute local filesystem path to the document to process, e.g.
     * {@code D:\\Loan.pdf} or {@code D://Loan.pdf}. Supported formats: .pdf, .docx, .txt.
     */
    @NotBlank(message = "documentPath is required")
    private String documentPath;
}
