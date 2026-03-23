package com.camunda.worker.capbpm;

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
 * CAPBPM — checks whether a customer with the given MSISDN already exists in the CapBPM system.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code customerExists} — true if found in CapBPM DB
 *   <li>{@code customerId} — internal customer ID (null when not found)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckCustomerExistsWorker {

    @JobWorker(type = "capbpm.checkCustomerExists", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(name = "customerExists") Boolean customerExistsOverride) {

        log.info(
                "[CAPBPM][CheckCustomerExists] type={} key={} msisdn={} customerExistsOverride={}",
                job.getType(),
                job.getKey(),
                msisdn,
                customerExistsOverride);

        try {
            boolean exists = customerExistsOverride != null
                    ? customerExistsOverride
                    : (msisdn.charAt(msisdn.length() - 1) - '0') % 2 != 0;
            String customerId = exists ? "CUST-" + msisdn.substring(msisdn.length() - 6) : null;

            log.info(
                    "[CAPBPM][CheckCustomerExists] msisdn={} source={} exists={} customerId={}",
                    msisdn,
                    customerExistsOverride != null ? "request" : "computed",
                    exists,
                    customerId);

            Map<String, Object> vars = new HashMap<>();
            vars.put("customerExists", exists);
            vars.put("customerId", customerId);
            vars.put("customerCheckedAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[CAPBPM][CheckCustomerExists] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
