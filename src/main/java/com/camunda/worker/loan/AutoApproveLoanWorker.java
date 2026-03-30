package com.camunda.worker.loan;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Auto-approves loan applications that are classified as LOW risk.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanApproved} — {@code true}
 *   <li>{@code approvalCode} — unique approval reference
 *   <li>{@code approvedAmount} — the disbursement amount (equals requested amount)
 *   <li>{@code approvedAt} — epoch millis of approval
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoApproveLoanWorker {

    @JobWorker(type = "loan.auto-approve", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicationId,
            @Variable String applicantName,
            @Variable double requestedAmount,
            @Variable String riskLevel,
            @Variable int riskScore) {

        log.info(
                "[Loan][AutoApprove] type={} key={} applicationId={} riskLevel={} riskScore={}",
                job.getType(),
                job.getKey(),
                applicationId,
                riskLevel,
                riskScore);

        try {
            String approvalCode =
                    "APR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            log.info(
                    "[Loan][AutoApprove] Approved applicationId={} applicant={} amount={} approvalCode={}",
                    applicationId,
                    applicantName,
                    requestedAmount,
                    approvalCode);

            Map<String, Object> vars = new HashMap<>();
            vars.put("loanApproved", true);
            vars.put("approvalCode", approvalCode);
            vars.put("approvedAmount", requestedAmount);
            vars.put("approvedAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[Loan][AutoApprove] Failed applicationId={}", applicationId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
