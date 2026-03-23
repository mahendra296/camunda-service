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
 * CAPBPM — publishes an OptedInNotification message to the Airtel process, informing it that the
 * customer has been successfully opted-in to the CapBPM loan program.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code optInNotified} — true when Airtel has been informed
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyOptedInWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "capbpm.notifyOptedIn", autoComplete = false)
    public void handle(
            JobClient client, ActivatedJob job, @Variable String msisdn, @Variable(optional = true) String customerId) {

        log.info(
                "[CAPBPM][NotifyOptedIn] type={} key={} msisdn={} customerId={}",
                job.getType(),
                job.getKey(),
                msisdn,
                customerId);

        try {
            camundaClient
                    .newPublishMessageCommand()
                    .messageName("OptedInNotification")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(Map.of(
                            "customerId",
                            customerId != null ? customerId : "",
                            "optInNotifiedAt",
                            System.currentTimeMillis()))
                    .send()
                    .join();

            log.info("[CAPBPM][NotifyOptedIn] OptedInNotification published to Airtel for msisdn={}", msisdn);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("optInNotified", true, "optInNotifiedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][NotifyOptedIn] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
