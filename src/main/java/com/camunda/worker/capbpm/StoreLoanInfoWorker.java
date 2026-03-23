package com.camunda.worker.capbpm;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CAPBPM — persists the loan application submitted by Airtel and assigns a loan reference ID.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanId} — newly assigned loan reference ID
 *   <li>{@code loanStored} — true on success
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoreLoanInfoWorker {

    @JobWorker(type = "capbpm.storeLoanInfo", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) Double loanAmount,
            @Variable(optional = true) Integer tenureMonths,
            @Variable(optional = true) String loanPurpose) {

        log.info(
                "[CAPBPM][StoreLoanInfo] type={} key={} msisdn={} amount={} tenure={} purpose={}",
                job.getType(),
                job.getKey(),
                msisdn,
                loanAmount,
                tenureMonths,
                loanPurpose);

        try {
            var loanId = "LOAN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

            log.info("[CAPBPM][StoreLoanInfo] Loan stored for msisdn={} loanId={}", msisdn, loanId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("loanId", loanId, "loanStored", true, "loanStoredAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][StoreLoanInfo] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
