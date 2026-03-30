package com.camunda.service;

import com.camunda.dto.LoanApplicationRequest;
import com.camunda.dto.LoanAssessmentResponse;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRiskAssessmentService {

    private final CamundaClient camundaClient;

    public LoanAssessmentResponse startAssessment(LoanApplicationRequest request) {
        log.info(
                "[LoanRiskAssessmentService] Starting assessment applicationId={} applicant={}",
                request.getApplicationId(),
                request.getApplicantName());

        var variables = buildVariables(request);

        ProcessInstanceEvent event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("loan-risk-assessment-process")
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        log.info(
                "[LoanRiskAssessmentService] Process started. instanceKey={} applicationId={}",
                event.getProcessInstanceKey(),
                request.getApplicationId());

        return new LoanAssessmentResponse(
                request.getApplicationId(),
                event.getProcessInstanceKey(),
                "STARTED",
                "Loan risk assessment started — workers will validate the application and evaluate risk rules");
    }

    // ──────────────────────────────────────────────
    private Map<String, Object> buildVariables(LoanApplicationRequest request) {
        var vars = new HashMap<String, Object>();
        vars.put("applicationId", request.getApplicationId());
        vars.put("applicantName", request.getApplicantName());
        vars.put("applicantAge", request.getApplicantAge());
        vars.put("monthlyIncome", request.getMonthlyIncome());
        vars.put("requestedAmount", request.getRequestedAmount());
        if (request.getCreditScore() != null) {
            vars.put("creditScore", request.getCreditScore());
        }
        vars.put("startedAt", System.currentTimeMillis());
        return vars;
    }
}
