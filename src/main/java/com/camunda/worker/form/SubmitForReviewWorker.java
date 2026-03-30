package com.camunda.worker.form;

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
 * Submits a validated loan application for further review.
 *
 * <p>Called only when {@code formValid = true}. Generates a submission reference and
 * records the submission timestamp.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code submissionRef} — unique reference for the submitted application
 *   <li>{@code estimatedProcessingDays} — estimated number of business days to process
 *   <li>{@code submittedAt} — epoch millis of submission
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitForReviewWorker {

    @JobWorker(type = "form.submit-for-review", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicantName,
            @Variable String emailAddress,
            @Variable double requestedAmount,
            @Variable String loanPurpose,
            @Variable String employmentType) {

        log.info("[Form][SubmitForReview] type={} key={} applicant={}", job.getType(), job.getKey(), applicantName);

        try {
            var submissionRef =
                    "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            int estimatedDays = estimateProcessingDays(requestedAmount, employmentType);

            log.info(
                    "[Form][SubmitForReview] Submitted applicant={} ref={} estimatedDays={} purpose={}",
                    applicantName,
                    submissionRef,
                    estimatedDays,
                    loanPurpose);

            // TODO: notify applicant via email (emailAddress)

            Map<String, Object> vars = new HashMap<>();
            vars.put("submissionRef", submissionRef);
            vars.put("estimatedProcessingDays", estimatedDays);
            vars.put("submittedAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[Form][SubmitForReview] Failed applicant={}", applicantName, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    // ──────────────────────────────────────────────
    private int estimateProcessingDays(double requestedAmount, String employmentType) {
        int base = requestedAmount > 50_000 ? 5 : 3;
        return "SELF_EMPLOYED".equals(employmentType) || "BUSINESS_OWNER".equals(employmentType) ? base + 2 : base;
    }
}
