package com.camunda.worker.capbpm;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CAPBPM — publishes a SubmissionConfirmation message to the Airtel process, acknowledging that
 * the loan application has been received and is being processed by CBS.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code submissionConfirmed} — true when Airtel has been notified
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendSubmissionConfirmationWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "capbpm.sendSubmissionConfirmation", autoComplete = false)
    public void handle(
            JobClient client, ActivatedJob job, @Variable String msisdn, @Variable(optional = true) String loanId) {

        log.info(
                "[CAPBPM][SendSubmissionConfirmation] type={} key={} msisdn={} loanId={}",
                job.getType(),
                job.getKey(),
                msisdn,
                loanId);

        try {
            camundaClient
                    .newPublishMessageCommand()
                    .messageName("SubmissionConfirmation")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(Map.of(
                            "loanId",
                            loanId != null ? loanId : "",
                            "submissionConfirmedAt",
                            System.currentTimeMillis()))
                    .send()
                    .join();

            log.info(
                    "[CAPBPM][SendSubmissionConfirmation] SubmissionConfirmation published to Airtel: msisdn={} loanId={}",
                    msisdn,
                    loanId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("submissionConfirmed", true, "submissionConfirmedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][SendSubmissionConfirmation] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
