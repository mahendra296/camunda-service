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
 * Sends a payment confirmation to the customer after a successful charge.
 *
 * <p>Runs only on the happy path (no error boundary on this task).
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code confirmationSent} — {@code true}</li>
 *   <li>{@code confirmationSentAt} — epoch millis</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPaymentConfirmationWorker {

    @JobWorker(type = "payment.send-confirmation", autoComplete = false)
    public void sendConfirmation(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String customerId,
            @Variable String transactionId,
            @Variable Double chargedAmount,
            @Variable String currency) {

        log.info(
                "[SendPaymentConfirmation] type={} key={} paymentId={} transactionId={}",
                job.getType(),
                job.getKey(),
                paymentId,
                transactionId);

        try {
            // Simulate sending email / push notification
            log.info(
                    "[SendPaymentConfirmation] Confirmation sent to customerId={} for paymentId={} amount={} {}",
                    customerId,
                    paymentId,
                    chargedAmount,
                    currency);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "confirmationSent", true,
                            "confirmationSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[SendPaymentConfirmation] Error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to send payment confirmation: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
