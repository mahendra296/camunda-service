package com.camunda.worker.loan;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Notifies the applicant that their loan application was rejected at the validation stage.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code rejectionNotificationSent} — always {@code true} after this task completes
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendRejectionNotificationWorker {

    @JobWorker(type = "loan.send-rejection-notification", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicationId,
            @Variable String applicantName,
            @Variable String validationError) {

        log.info(
                "[Loan][SendRejectionNotification] type={} key={} applicationId={} reason={}",
                job.getType(),
                job.getKey(),
                applicationId,
                validationError);

        try {
            // TODO: integrate with notification service (email / SMS)
            log.info(
                    "[Loan][SendRejectionNotification] Notifying {} — applicationId={} reason={}",
                    applicantName,
                    applicationId,
                    validationError);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("rejectionNotificationSent", true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[Loan][SendRejectionNotification] Failed applicationId={}", applicationId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
