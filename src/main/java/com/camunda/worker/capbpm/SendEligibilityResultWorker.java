package com.camunda.worker.capbpm;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CAPBPM — publishes an EligibilityResult message to the Airtel process carrying the credit score
 * outcome. The Airtel gateway then decides whether to submit a loan application.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code eligibilityNotified} — true when Airtel has received the result
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendEligibilityResultWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "capbpm.sendEligibilityResult", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) Boolean eligible,
            @Variable(optional = true) Integer creditScore,
            @Variable(optional = true) String creditGrade) {

        log.info(
                "[CAPBPM][SendEligibilityResult] type={} key={} msisdn={} eligible={} score={} grade={}",
                job.getType(),
                job.getKey(),
                msisdn,
                eligible,
                creditScore,
                creditGrade);

        try {
            var msgVars = new HashMap<String, Object>();
            msgVars.put("eligible", eligible != null && eligible);
            msgVars.put("creditScore", creditScore);
            msgVars.put("creditGrade", creditGrade);
            msgVars.put("eligibilityResultSentAt", System.currentTimeMillis());

            camundaClient
                    .newPublishMessageCommand()
                    .messageName("EligibilityResult")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(msgVars)
                    .send()
                    .join();

            log.info(
                    "[CAPBPM][SendEligibilityResult] EligibilityResult published to Airtel: msisdn={} eligible={}",
                    msisdn,
                    eligible);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "eligibilityNotified",
                            true,
                            "eligibilityNotifiedAt",
                            System.currentTimeMillis(),
                            "eligible",
                            eligible != null && eligible))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][SendEligibilityResult] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
