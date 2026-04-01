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
 * Handles the payment error after the ERROR BOUNDARY EVENT fires on {@code task_charge_payment}.
 *
 * <p>This is the first task on the error-handling path. It receives the error details
 * and logs them before the process triggers compensation and sends a failure notification.
 *
 * <p>Flow context:
 * <pre>
 *   boundary_payment_failed_error (PAYMENT_FAILED)
 *     → task_handle_payment_error  ← THIS WORKER
 *     → throw_compensation          (triggers task_reverse_reservation)
 *     → task_send_payment_failure
 *     → end_payment_failed
 * </pre>
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code errorLoggedAt} — epoch millis</li>
 *   <li>{@code paymentFailureReason} — human-readable reason from the BPMN error message</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandlePaymentErrorWorker {

    @JobWorker(type = "payment.handle-error", autoComplete = false)
    public void handlePaymentError(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String customerId,
            @Variable Double amount,
            @Variable(optional = true) String errorMessage) {

        log.info(
                "[HandlePaymentError] type={} key={} paymentId={} — logging payment failure",
                job.getType(),
                job.getKey(),
                paymentId);

        try {
            var failureReason = errorMessage != null ? errorMessage : "Payment failed — no reason provided";
            log.error(
                    "[HandlePaymentError] PAYMENT FAILED paymentId={} customerId={} amount={} reason={}",
                    paymentId,
                    customerId,
                    amount,
                    failureReason);

            // Persist to audit log / alert system in a real implementation

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "errorLoggedAt", System.currentTimeMillis(),
                            "paymentFailureReason", failureReason))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[HandlePaymentError] Unexpected error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to log payment error: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
