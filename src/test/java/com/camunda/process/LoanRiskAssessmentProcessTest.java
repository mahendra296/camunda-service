package com.camunda.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class LoanRiskAssessmentProcessTest {

    private static final String PROCESS_ID = "loan-risk-assessment-process";

    private static final Map<String, Object> DEFAULT_RISK_CONFIG = Map.ofEntries(
            Map.entry("csExcellentMin", 750),
            Map.entry("csExcellentMax", 850),
            Map.entry("csVeryGoodMin", 700),
            Map.entry("csVeryGoodMax", 749),
            Map.entry("csGoodMin", 650),
            Map.entry("csAcceptableMin", 600),
            Map.entry("csFairMin", 500),
            Map.entry("csFairMax", 599),
            Map.entry("csPoorMax", 500),
            Map.entry("dtiVeryLow", 0.25),
            Map.entry("dtiLow", 0.30),
            Map.entry("dtiBorderline", 0.35),
            Map.entry("dtiMedium", 0.40),
            Map.entry("dtiHigh", 0.50),
            Map.entry("ageStandard", 25),
            Map.entry("ageMature", 30),
            Map.entry("ageYoungMin", 18),
            Map.entry("ageYoungMax", 24));

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/loan-risk-assessment-process.bpmn")
                .addResourceFromClasspath("workflow/loan-risk-rule.dmn")
                .send()
                .join();
    }

    private ProcessInstanceEvent startAssessment() {
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .send()
                .join();
    }

    private static Map<String, Object> applicationResponse(
            boolean valid, int creditScore, int applicantAge, int monthlyIncome, int requestedAmount) {
        var response = new HashMap<String, Object>();
        response.put("applicationValid", valid);
        response.put("creditScore", creditScore);
        response.put("applicantAge", applicantAge);
        response.put("monthlyIncome", monthlyIncome);
        response.put("requestedAmount", requestedAmount);
        response.put("riskConfig", DEFAULT_RISK_CONFIG);
        return response;
    }

    @Test
    void invalidApplication_isRejectedBeforeRiskEvaluation() {
        processTestContext
                .mockJobWorker("loan.validate-application")
                .thenComplete(Map.of("validateApplicationResponse", applicationResponse(false, 0, 0, 0, 0)));
        processTestContext.mockJobWorker("loan.send-rejection-notification").thenComplete();

        var instance = startAssessment();

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder("task_send_rejection_notification", "end_application_invalid")
                .hasNotActivatedElements("task_evaluate_risk_rules");
    }

    @Test
    void excellentCreditLowDti_autoApprovesAsLowRisk() {
        // creditScore=800 (Excellent), dti=20000/(10000*12)=0.167 (<=dtiVeryLow), age=30 -> rule_1 -> LOW
        processTestContext
                .mockJobWorker("loan.validate-application")
                .thenComplete(Map.of("validateApplicationResponse", applicationResponse(true, 800, 30, 10000, 20000)));
        processTestContext.mockJobWorker("loan.auto-approve").thenComplete();

        var instance = startAssessment();

        assertThat(instance)
                .isCompleted()
                .hasVariable("riskDecision", Map.of("riskLevel", "LOW", "riskScore", 10))
                .hasCompletedElementsInOrder("task_evaluate_risk_rules", "task_auto_approve_loan", "end_loan_approved");
    }

    @Test
    void goodCreditMediumDti_goesToManualReviewAsMediumRisk() {
        // creditScore=680 (Good/VeryGood band), dti=40000/(10000*12)=0.33 (<=dtiMedium), age=35 -> rule_4 -> MEDIUM
        processTestContext
                .mockJobWorker("loan.validate-application")
                .thenComplete(Map.of("validateApplicationResponse", applicationResponse(true, 680, 35, 10000, 40000)));

        var instance = startAssessment();

        assertThat(instance).hasCompletedElements("task_evaluate_risk_rules");
        processTestContext.completeUserTask("task_manual_review_loan");

        assertThat(instance)
                .isCompleted()
                .hasVariable("riskDecision", Map.of("riskLevel", "MEDIUM", "riskScore", 45))
                .hasCompletedElementsInOrder("task_manual_review_loan", "end_review_pending");
    }

    @Test
    void poorCredit_autoRejectsAsHighRisk() {
        // creditScore=450 (Poor, <= csPoorMax), dti=30000/(10000*12)=0.25 (<=dtiBorderline) -> rule_8 -> HIGH
        processTestContext
                .mockJobWorker("loan.validate-application")
                .thenComplete(Map.of("validateApplicationResponse", applicationResponse(true, 450, 40, 10000, 30000)));
        processTestContext.mockJobWorker("loan.auto-reject").thenComplete();

        var instance = startAssessment();

        assertThat(instance)
                .isCompleted()
                .hasVariable("riskDecision", Map.of("riskLevel", "HIGH", "riskScore", 85))
                .hasCompletedElementsInOrder("task_evaluate_risk_rules", "task_auto_reject_loan", "end_loan_rejected");
    }
}
