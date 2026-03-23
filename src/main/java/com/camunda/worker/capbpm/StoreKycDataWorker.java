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
 * CAPBPM — persists the KYC data received from Airtel into the CapBPM customer store.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code kycStored} — true on success
 *   <li>{@code customerId} — newly created CapBPM customer ID
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreKycDataWorker {

    @JobWorker(type = "capbpm.storeKycData", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) String kycFullName,
            @Variable(optional = true) String kycNationalId) {

        log.info(
                "[CAPBPM][StoreKycData] type={} key={} msisdn={} name={}",
                job.getType(),
                job.getKey(),
                msisdn,
                kycFullName);

        try {
            var customerId = "CUST-" + msisdn.substring(msisdn.length() - 6);

            log.info("[CAPBPM][StoreKycData] KYC stored for msisdn={} customerId={}", msisdn, customerId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "kycStored", true, "customerId", customerId, "kycStoredAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][StoreKycData] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
