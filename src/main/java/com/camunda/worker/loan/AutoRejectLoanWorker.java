package com.camunda.worker.loan;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Auto-rejects loan applications classified as HIGH risk.
 *
 * <p>{@code riskLevel} and {@code riskScore} are produced by the DMN decision table
 * ({@code loan-risk-rules.dmn}) and unpacked into process variables via the BPMN output mapping on
 * the Business Rule Task.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanApproved} — {@code false}
 *   <li>{@code rejectionReason} — human-readable rejection rationale derived from the DMN score
 *   <li>{@code rejectedAt} — epoch millis of rejection
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoRejectLoanWorker {

    @JobWorker(type = "loan.auto-reject", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicationId,
            @Variable String applicantName,
            @Variable String riskLevel,
            @Variable int riskScore) {

        log.info(
                "[Loan][AutoReject] type={} key={} applicationId={} riskLevel={} riskScore={}",
                job.getType(),
                job.getKey(),
                applicationId,
                riskLevel,
                riskScore);

        try {
            String rejectionReason = buildRejectionReason(riskScore);

            log.info(
                    "[Loan][AutoReject] Rejected applicationId={} applicant={} reason={}",
                    applicationId,
                    applicantName,
                    rejectionReason);

            Map<String, Object> vars = new HashMap<>();
            vars.put("loanApproved", false);
            vars.put("rejectionReason", rejectionReason);
            vars.put("rejectedAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[Loan][AutoReject] Failed applicationId={}", applicationId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    // ──────────────────────────────────────────────
    private String buildRejectionReason(int riskScore) {
        if (riskScore >= 80) {
            return "Application does not meet minimum credit and debt-to-income requirements";
        }
        return "Risk score (" + riskScore + ") exceeds acceptable threshold — credit profile or debt burden too high";
    }
}
