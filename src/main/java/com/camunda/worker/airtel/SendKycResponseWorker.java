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
 * AIRTEL — publishes the {@code KycResponse} message to Zeebe, correlating to the CAPBPM process
 * instance waiting at {@code catch_kyc_response}. The KYC variables collected by
 * {@code ProvideKycDataWorker} are forwarded as message payload.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code kycResponseSent} — true when the message has been published
 *   <li>{@code kycResponseSentAt} — epoch millis when the message was published
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendKycResponseWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "airtel.sendKycResponse", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) String kycFullName,
            @Variable(optional = true) String kycNationalId,
            @Variable(optional = true) String kycDob,
            @Variable(optional = true) String kycEmail) {

        log.info("[AIRTEL][SendKycResponse] type={} key={} msisdn={}", job.getType(), job.getKey(), msisdn);

        try {
            var msgVars = new HashMap<String, Object>();
            msgVars.put("msisdn", msisdn);
            msgVars.put("kycFullName", kycFullName);
            msgVars.put("kycNationalId", kycNationalId);
            msgVars.put("kycDob", kycDob);
            msgVars.put("kycEmail", kycEmail);
            msgVars.put("kycResponseSentAt", System.currentTimeMillis());

            camundaClient
                    .newPublishMessageCommand()
                    .messageName("KycResponse")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(msgVars)
                    .send()
                    .join();

            log.info("[AIRTEL][SendKycResponse] KycResponse published to CAPBPM for msisdn={}", msisdn);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("kycResponseSent", true, "kycResponseSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[AIRTEL][SendKycResponse] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
