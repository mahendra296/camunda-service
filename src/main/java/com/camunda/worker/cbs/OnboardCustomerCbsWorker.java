package com.camunda.worker.cbs;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CBS integration — onboards a new customer into the Core Banking System.
 *
 * <p>Called only when {@code customerInCbs = false}. Creates a new CBS account for the customer.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code cbsAccountNo} — newly created CBS account number
 *   <li>{@code cbsOnboarded} — true on success
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardCustomerCbsWorker {

    @JobWorker(type = "cbs.onboardCustomer", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) String customerId,
            @Variable(optional = true) String kycFullName) {

        log.info(
                "[CBS][OnboardCustomer] type={} key={} msisdn={} customerId={} name={}",
                job.getType(),
                job.getKey(),
                msisdn,
                customerId,
                kycFullName);

        try {
            var cbsAccountNo = "CBS-NEW-" + msisdn.substring(msisdn.length() - 6);

            log.info("[CBS][OnboardCustomer] Customer onboarded: msisdn={} cbsAccountNo={}", msisdn, cbsAccountNo);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "cbsAccountNo",
                            cbsAccountNo,
                            "cbsOnboarded",
                            true,
                            "cbsOnboardedAt",
                            System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CBS][OnboardCustomer] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
