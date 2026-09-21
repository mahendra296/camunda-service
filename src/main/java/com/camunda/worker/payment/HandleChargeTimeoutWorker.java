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
 * Triggered by the NON-INTERRUPTING TIMER BOUNDARY EVENT on {@code task_charge_payment}
 * after 30 seconds elapse without the task completing.
 *
 * <p>Key distinctions from the error boundary path:
 * <ul>
 *   <li>Non-interrupting: {@code task_charge_payment} continues running in parallel — the
 *       charge may still succeed or fail independently.</li>
 *   <li>This task logs the slowness and forwards a timeout token to {@code gw_error_or_timeout}.</li>
 * </ul>
 *
 * <p>MERGE GATEWAY context ({@code gw_error_or_timeout}):
 * <pre>
 *   boundary_payment_failed_error (PAYMENT_FAILED) ────────────────────────────┐
 *                                                                              ▼
 *   boundary_charge_timeout → HandleChargeTimeoutWorker → gw_error_or_timeout → Log Failure → ...
 * </pre>
 * An exclusive gateway is used as an uncontrolled merge — Zeebe does not support BPMN
 * complex gateways — so the flow proceeds as soon as either the error token or this
 * timeout token arrives, without waiting for the other.
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code timeoutDetectedAt} — epoch millis</li>
 *   <li>{@code paymentFailureReason} — set to {@code "CHARGE_TIMEOUT"} so the downstream
 *       log task and failure notification have context about why the payment failed</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandleChargeTimeoutWorker {

    @JobWorker(type = "payment.handle-timeout", autoComplete = false)
    public void handleChargeTimeout(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String customerId,
            @Variable(optional = true) Double amount) {

        log.info(
                "[HandleChargeTimeout] TIMER-BOUNDARY type={} key={} paymentId={} — charge timed out after 30s",
                job.getType(),
                job.getKey(),
                paymentId);

        try {
            log.warn(
                    "[HandleChargeTimeout] Payment gateway did not respond within 30s"
                            + " paymentId={} customerId={} amount={}",
                    paymentId,
                    customerId,
                    amount);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "timeoutDetectedAt",
                            System.currentTimeMillis(),
                            "paymentFailureReason",
                            "CHARGE_TIMEOUT — gateway did not respond within 30s"))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[HandleChargeTimeout] Unexpected error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to handle charge timeout: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
