package com.camunda.worker.capbpm;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CAPBPM — marks the customer as opted-in to the CapBPM loan product.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code optedIn} — true on success
 *   <li>{@code optInTimestamp} — epoch-ms when opt-in was recorded
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptInCustomerWorker {

    @JobWorker(type = "capbpm.optInCustomer", autoComplete = false)
    public void handle(
            JobClient client, ActivatedJob job, @Variable String msisdn, @Variable(optional = true) String customerId) {

        log.info(
                "[CAPBPM][OptInCustomer] type={} key={} msisdn={} customerId={}",
                job.getType(),
                job.getKey(),
                msisdn,
                customerId);

        try {
            long optInTimestamp = System.currentTimeMillis();

            log.info("[CAPBPM][OptInCustomer] msisdn={} opted-in at={}", msisdn, optInTimestamp);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("optedIn", true, "optInTimestamp", optInTimestamp))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][OptInCustomer] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
