package com.camunda.worker.payment;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fires from the INCLUSIVE GATEWAY split when {@code amount > 500}.
 *
 * <p>One of up to three notification paths activated by {@code gw_inclusive_notify}.
 * The {@code gw_inclusive_join} waits for this task only if the inclusive split
 * activated it (i.e. the condition {@code =amount > 500} was true at split time).
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code smsAlertSent} — {@code true}</li>
 *   <li>{@code smsAlertSentAt} — epoch millis</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendSmsAlertWorker {

    @JobWorker(type = "payment.send-sms-alert", autoComplete = false)
    public void sendSmsAlert(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String customerId,
            @Variable Double amount,
            @Variable(optional = true) String transactionId) {

        log.info(
                "[SendSmsAlert] INCLUSIVE-BRANCH type={} key={} paymentId={} amount={}",
                job.getType(),
                job.getKey(),
                paymentId,
                amount);

        try {
            log.info(
                    "[SendSmsAlert] Sending high-value SMS alert to customerId={} for paymentId={} amount={}",
                    customerId,
                    paymentId,
                    amount);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "smsAlertSent", true,
                            "smsAlertSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[SendSmsAlert] Error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to send SMS alert: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
