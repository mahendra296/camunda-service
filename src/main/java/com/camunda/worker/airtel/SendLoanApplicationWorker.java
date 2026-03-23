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
 * AIRTEL — publishes the {@code LoanApplication} message to Zeebe, correlating to the CAPBPM
 * process instance waiting at {@code catch_loan_application}. Carries the confirmed loan
 * parameters as message payload.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanApplicationSent} — true when the message has been published
 *   <li>{@code loanApplicationSentAt} — epoch millis when the message was published
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendLoanApplicationWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "airtel.sendLoanApplication", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) Double loanAmount,
            @Variable(optional = true) Integer tenureMonths,
            @Variable(optional = true) String loanPurpose,
            @Variable(optional = true) Integer creditScore,
            @Variable(optional = true) String creditGrade) {

        log.info(
                "[AIRTEL][SendLoanApplication] type={} key={} msisdn={} amount={} tenure={}",
                job.getType(),
                job.getKey(),
                msisdn,
                loanAmount,
                tenureMonths);

        try {
            var msgVars = new HashMap<String, Object>();
            msgVars.put("msisdn", msisdn);
            msgVars.put("loanAmount", loanAmount);
            msgVars.put("tenureMonths", tenureMonths);
            msgVars.put("loanPurpose", loanPurpose != null ? loanPurpose : "PERSONAL");
            msgVars.put("creditScore", creditScore);
            msgVars.put("creditGrade", creditGrade);
            msgVars.put("loanApplicationSentAt", System.currentTimeMillis());

            camundaClient
                    .newPublishMessageCommand()
                    .messageName("LoanApplication")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(msgVars)
                    .send()
                    .join();

            log.info("[AIRTEL][SendLoanApplication] LoanApplication published to CAPBPM for msisdn={}", msisdn);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("loanApplicationSent", true, "loanApplicationSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[AIRTEL][SendLoanApplication] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
