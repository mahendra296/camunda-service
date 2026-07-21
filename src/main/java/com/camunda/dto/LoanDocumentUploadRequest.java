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
     * Absolute local filesystem path to the document for the "Structured PDF Extract" branch
     * (boxed/scanned forms, native field detection).
     */
    @NotBlank(message = "documentPath is required")
    private String documentPath;

    /**
     * Absolute local filesystem path to the document for the "Unstructured PDF Extract" branch
     * (freeform, text-native PDFs, LLM-mapped taxonomy).
     */
    @NotBlank(message = "unstructuredDocumentPath is required")
    private String unstructuredDocumentPath;

    /**
     * Absolute local filesystem path to the document for the "Unstructured PDF With Image
     * Extract" branch (scanned/photographed PDFs with no embedded text).
     */
    @NotBlank(message = "unstructuredImageDocumentPath is required")
    private String unstructuredImageDocumentPath;

    private int exportType;
}
