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

    /**
     * Optional OCR/IDP confidence override (0.0-1.0) to force the low-confidence /
     * human-review path during testing. When omitted, the extraction worker derives
     * confidence from how many labeled fields it could find in the document text.
     */
    private Double simulatedConfidence;
}
