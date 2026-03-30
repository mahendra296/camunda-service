package com.camunda.service;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanApplicationFormService {

    private final CamundaClient camundaClient;

    /**
     * Starts the loan-application-form-process. Generates a tracking ID and stores it as a
     * process variable so the form pre-populates it in read-only fields.
     */
    public Map<String, Object> startFormProcess() {
        var trackingId = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("[LoanApplicationFormService] Starting form process trackingId={}", trackingId);

        ProcessInstanceEvent event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("loan-application-form-process")
                .latestVersion()
                .variables(Map.of("trackingId", trackingId, "startedAt", System.currentTimeMillis()))
                .send()
                .join();

        log.info(
                "[LoanApplicationFormService] Process started instanceKey={} trackingId={}",
                event.getProcessInstanceKey(),
                trackingId);

        return Map.of(
                "trackingId",
                trackingId,
                "processInstanceKey",
                event.getProcessInstanceKey(),
                "status",
                "STARTED",
                "message",
                "Form process started — retrieve the user task key from Camunda Tasklist or GET /v2/user-tasks to submit the form");
    }

    /**
     * Completes the "Fill Loan Application Form" Camunda user task.
     *
     * <p>Uses {@code CamundaClient.newCompleteUserTaskCommand} (not {@code JobClient}) because the
     * task uses {@code <zeebe:userTask />} implementation. The {@code userTaskKey} must be obtained
     * from Camunda Tasklist or {@code GET /v2/user-tasks?processInstanceKey=...}.
     */
    public void completeFormTask(long userTaskKey, Map<String, Object> formData) {
        log.info(
                "[LoanApplicationFormService] Completing form task userTaskKey={} applicant={}",
                userTaskKey,
                formData.get("applicantName"));

        camundaClient
                .newCompleteUserTaskCommand(userTaskKey)
                .variables(formData)
                .send()
                .join();
    }
}
