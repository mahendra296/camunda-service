package com.camunda.worker.airtel;

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
 * AIRTEL — publishes the {@code LoanRequest} message to Zeebe, which triggers the
 * {@code capbpm_start} message start event and creates a new CAPBPM process instance.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanRequestSent} — true when the message has been published
 *   <li>{@code loanRequestSentAt} — epoch millis when the message was published
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendLoanRequestWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "airtel.sendLoanRequest", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) Double loanAmount,
            @Variable(optional = true) Integer tenureMonths,
            @Variable(optional = true) String loanPurpose,
            @Variable(optional = true) Boolean customerExists) {

        log.info("[AIRTEL][SendLoanRequest] type={} key={} msisdn={}", job.getType(), job.getKey(), msisdn);

        try {
            var msgVars = new HashMap<String, Object>();
            msgVars.put("msisdn", msisdn);
            msgVars.put("loanAmount", loanAmount);
            msgVars.put("tenureMonths", tenureMonths);
            msgVars.put("customerExists", customerExists);
            msgVars.put("loanPurpose", loanPurpose != null ? loanPurpose : "PERSONAL");
            msgVars.put("loanRequestSentAt", System.currentTimeMillis());

            camundaClient
                    .newPublishMessageCommand()
                    .messageName("LoanRequest")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(5))
                    .variables(msgVars)
                    .send()
                    .join();

            log.info("[AIRTEL][SendLoanRequest] LoanRequest published — CAPBPM process started for msisdn={}", msisdn);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("loanRequestSent", true, "loanRequestSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[AIRTEL][SendLoanRequest] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
