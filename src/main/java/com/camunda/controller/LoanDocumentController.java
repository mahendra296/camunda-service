package com.camunda.controller;

import com.camunda.dto.LoanDocumentProcessResponse;
import com.camunda.dto.LoanDocumentUploadRequest;
import com.camunda.service.LoanDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/loan-documents")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class LoanDocumentController {

    private final LoanDocumentService loanDocumentService;

    /**
     * Starts the loan document IDP (Intelligent Document Processing) flow.
     *
     * <p>POST /api/loan-documents/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<LoanDocumentProcessResponse> upload(@Valid @RequestBody LoanDocumentUploadRequest request) {
        log.info("[API] POST /api/loan-documents/upload documentId={}", request.getDocumentId());
        var response = loanDocumentService.startProcessing(request);
        return ResponseEntity.ok(response);
    }
}
