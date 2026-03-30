package com.camunda.controller;

import com.camunda.dto.LoanApplicationRequest;
import com.camunda.dto.LoanAssessmentResponse;
import com.camunda.service.LoanRiskAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanRiskAssessmentController {

    private final LoanRiskAssessmentService loanRiskAssessmentService;

    /**
     * Starts the loan risk assessment process.
     *
     * <p>POST /api/loans/assess
     */
    @PostMapping("/assess")
    public ResponseEntity<LoanAssessmentResponse> assess(@Valid @RequestBody LoanApplicationRequest request) {
        log.info("[API] POST /api/loans/assess applicationId={}", request.getApplicationId());
        var response = loanRiskAssessmentService.startAssessment(request);
        return ResponseEntity.ok(response);
    }
}
