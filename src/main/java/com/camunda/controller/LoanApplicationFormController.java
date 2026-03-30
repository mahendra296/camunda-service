package com.camunda.controller;

import com.camunda.service.LoanApplicationFormService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/loan-forms")
@RequiredArgsConstructor
public class LoanApplicationFormController {

    private final LoanApplicationFormService loanApplicationFormService;

    /**
     * Starts the loan-application-form-process.
     * The process immediately reaches the Fill Application Form user task.
     *
     * <p>POST /api/loan-forms/start
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        log.info("[API] POST /api/loan-forms/start");
        return ResponseEntity.ok(loanApplicationFormService.startFormProcess());
    }

    /**
     * Completes the "Fill Loan Application Form" Camunda user task.
     * Pass all form fields in the request body.
     * Obtain {{taskKey}} from Camunda Tasklist or:
     *   GET http://localhost:8080/v2/user-tasks?processInstanceKey={instanceKey}
     *
     * <p>POST /api/loan-forms/{taskKey}/submit
     */
    @PostMapping("/{taskKey}/submit")
    public ResponseEntity<Map<String, Object>> submitForm(
            @PathVariable long taskKey, @RequestBody Map<String, Object> formData) {

        log.info("[API] POST /api/loan-forms/{}/submit applicant={}", taskKey, formData.get("applicantName"));
        loanApplicationFormService.completeFormTask(taskKey, formData);

        return ResponseEntity.ok(Map.of(
                "status", "FORM_SUBMITTED",
                "taskKey", taskKey,
                "message", "Form submitted — ValidateApplicationDataWorker will process it next"));
    }
}
