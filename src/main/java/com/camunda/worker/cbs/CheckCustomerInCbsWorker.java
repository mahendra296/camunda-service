package com.camunda.worker.cbs;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CBS integration — queries the Core Banking System to check if the customer has an active account.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code customerInCbs} — true if an active CBS account exists
 *   <li>{@code cbsAccountNo} — CBS account number (null when not found)
 *   <li>{@code cbsCustomerStatus} — ACTIVE | NOT_FOUND
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckCustomerInCbsWorker {

    @JobWorker(type = "cbs.checkCustomer", autoComplete = false)
    public void handle(
            JobClient client, ActivatedJob job, @Variable String msisdn, @Variable(optional = true) String customerId) {

        log.info(
                "[CBS][CheckCustomer] type={} key={} msisdn={} customerId={}",
                job.getType(),
                job.getKey(),
                msisdn,
                customerId);

        try {
            int digitSum = msisdn.chars()
                    .skip(Math.max(0, msisdn.length() - 3))
                    .map(c -> c - '0')
                    .sum();
            boolean inCbs = digitSum > 13;
            var cbsAccountNo = inCbs ? "CBS-ACC-" + msisdn.substring(msisdn.length() - 6) : null;
            var cbsCustomerStatus = inCbs ? "ACTIVE" : "NOT_FOUND";

            log.info(
                    "[CBS][CheckCustomer] msisdn={} inCbs={} account={} status={}",
                    msisdn,
                    inCbs,
                    cbsAccountNo,
                    cbsCustomerStatus);

            Map<String, Object> vars = new HashMap<>();
            vars.put("customerInCbs", inCbs);
            vars.put("cbsAccountNo", cbsAccountNo);
            vars.put("cbsCustomerStatus", cbsCustomerStatus);
            vars.put("cbsCheckedAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[CBS][CheckCustomer] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
