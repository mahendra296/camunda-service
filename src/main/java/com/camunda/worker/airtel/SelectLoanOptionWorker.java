package com.camunda.worker.airtel;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AIRTEL — simulates the customer navigating the USSD menu and selecting the "Apply Loan" option.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanOptionSelected} — true when the menu option is confirmed
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectLoanOptionWorker {

    @JobWorker(type = "airtel.selectLoanOption", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job, @Variable String msisdn) {

        log.info("[AIRTEL][SelectLoanOption] type={} key={} msisdn={}", job.getType(), job.getKey(), msisdn);

        try {
            log.info("[AIRTEL][SelectLoanOption] Customer msisdn={} selected Apply Loan option", msisdn);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("loanOptionSelected", true, "loanOptionSelectedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[AIRTEL][SelectLoanOption] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
