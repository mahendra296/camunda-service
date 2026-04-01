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
 * Sends a payment failure notification to the customer.
 *
 * <p>This is the last task on the error-handling path — it runs AFTER compensation
 * has completed (all compensation handlers have finished). The process then ends
 * at {@code end_payment_failed}.
 *
 * <p>Flow context:
 * <pre>
 *   throw_compensation (compensation complete)
 *     → task_send_payment_failure  ← THIS WORKER
 *     → end_payment_failed
 * </pre>
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code failureNotificationSent} — {@code true}</li>
 *   <li>{@code failureNotificationSentAt} — epoch millis</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPaymentFailureNotificationWorker {

    @JobWorker(type = "payment.send-failure-notification", autoComplete = false)
    public void sendFailureNotification(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String customerId,
            @Variable(optional = true) String paymentFailureReason) {

        log.info(
                "[SendPaymentFailureNotification] type={} key={} paymentId={}",
                job.getType(),
                job.getKey(),
                paymentId);

        try {
            log.info(
                    "[SendPaymentFailureNotification] Sending failure notification to customerId={}"
                            + " paymentId={} reason={}",
                    customerId,
                    paymentId,
                    paymentFailureReason);

            // Simulate sending SMS / email / push notification
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "failureNotificationSent", true,
                            "failureNotificationSentAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[SendPaymentFailureNotification] Error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to send failure notification: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
