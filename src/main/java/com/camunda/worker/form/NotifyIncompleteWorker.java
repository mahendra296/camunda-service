package com.camunda.worker.form;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Notifies the applicant that their form submission failed validation.
 *
 * <p>Called only when {@code formValid = false}.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code incompleteNotificationSent} — always {@code true} after this task completes
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyIncompleteWorker {

    @JobWorker(type = "form.notify-incomplete", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicantName,
            @Variable(name = "emailAddress") String emailAddress,
            @Variable String formValidationError) {

        log.info(
                "[Form][NotifyIncomplete] type={} key={} applicant={} reason={}",
                job.getType(),
                job.getKey(),
                applicantName,
                formValidationError);

        try {
            // TODO: send email/SMS to applicant (emailAddress) with formValidationError
            log.info(
                    "[Form][NotifyIncomplete] Notifying {} ({}) — reason: {}",
                    applicantName,
                    emailAddress,
                    formValidationError);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("incompleteNotificationSent", true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[Form][NotifyIncomplete] Failed applicant={}", applicantName, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
