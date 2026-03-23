package com.camunda.worker.airtel;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AIRTEL — retrieves KYC data from Airtel's internal subscriber registry and prepares it for
 * the KycResponse message that will be sent to CapBPM.
 *
 * <p>Output variables (carried forward to the KycResponse message throw event):
 *
 * <ul>
 *   <li>{@code kycFullName} — subscriber full name
 *   <li>{@code kycNationalId} — subscriber national ID number
 *   <li>{@code kycDob} — date of birth (ISO-8601)
 *   <li>{@code kycEmail} — email on record
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvideKycDataWorker {

    @JobWorker(type = "airtel.provideKycData", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job, @Variable String msisdn) {

        log.info("[AIRTEL][ProvideKycData] type={} key={} msisdn={}", job.getType(), job.getKey(), msisdn);

        try {
            // In production: query Airtel subscriber DB for KYC data.
            var vars = new HashMap<String, Object>();
            vars.put("kycFullName", "Subscriber " + msisdn.substring(msisdn.length() - 4));
            vars.put("kycNationalId", "NID" + msisdn.substring(msisdn.length() - 6));
            vars.put("kycDob", "1990-01-15");
            vars.put("kycEmail", "sub" + msisdn.substring(msisdn.length() - 4) + "@airtel.example.com");
            vars.put("kycFetched", true);
            vars.put("kycFetchedAt", System.currentTimeMillis());

            log.info("[AIRTEL][ProvideKycData] KYC data prepared for msisdn={}", msisdn);

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[AIRTEL][ProvideKycData] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
