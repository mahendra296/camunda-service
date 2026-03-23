package com.camunda.worker.cbs;

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
 * CBS integration — submits the loan disbursement instruction to the Core Banking System.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code disbursementRef} — CBS disbursement transaction reference
 *   <li>{@code disbursed} — true on success
 *   <li>{@code disbursedAmount} — actual amount disbursed
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessLoanDisbursementWorker {

    @JobWorker(type = "cbs.processLoanDisbursement", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) String loanId,
            @Variable(optional = true) String cbsAccountNo,
            @Variable(optional = true) Double loanAmount) {

        log.info(
                "[CBS][ProcessLoanDisbursement] type={} key={} msisdn={} loanId={} account={} amount={}",
                job.getType(),
                job.getKey(),
                msisdn,
                loanId,
                cbsAccountNo,
                loanAmount);

        try {
            var disbursementRef =
                    "DISB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            double disbursedAmount = loanAmount != null ? loanAmount : 0.0;

            log.info(
                    "[CBS][ProcessLoanDisbursement] Disbursed: msisdn={} loanId={} ref={} amount={}",
                    msisdn,
                    loanId,
                    disbursementRef,
                    disbursedAmount);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "disbursementRef",
                            disbursementRef,
                            "disbursed",
                            true,
                            "disbursedAmount",
                            disbursedAmount,
                            "disbursedAt",
                            System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CBS][ProcessLoanDisbursement] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
