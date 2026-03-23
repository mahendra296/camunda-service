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
 * CAPBPM — sends a KycRequest message to the Airtel process, then the CapBPM process waits at
 * {@code catch_kyc_response} for Airtel to respond with the KycResponse message.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code kycRequested} — true when the KycRequest has been published to Airtel
 *   <li>{@code kycRequestedAt} — epoch millis when the request was sent
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchKycFromAirtelWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "capbpm.fetchKycFromAirtel", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job, @Variable String msisdn) {

        log.info("[CAPBPM][FetchKycFromAirtel] type={} key={} msisdn={}", job.getType(), job.getKey(), msisdn);

        try {
            // Publish KycRequest to the Airtel process (correlated by msisdn).
            // The Airtel process is waiting at airtel_recv_kyc_request.
            camundaClient
                    .newPublishMessageCommand()
                    .messageName("KycRequest")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(Map.of("kycRequestSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

            log.info("[CAPBPM][FetchKycFromAirtel] KycRequest published to Airtel for msisdn={}", msisdn);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("kycRequested", true, "kycRequestedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][FetchKycFromAirtel] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
