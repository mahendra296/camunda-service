package com.camunda.worker.payment;

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
 * Charges the payment gateway.
 *
 * <p>This task has TWO boundary events in the BPMN:
 * <ol>
 *   <li><b>ERROR Boundary</b> ({@code boundary_payment_failed_error}, interrupting):
 *       Catches {@code PAYMENT_FAILED}. When this worker throws the BPMN error, the task
 *       is cancelled and the process is routed to the error-handling path where a
 *       compensation throw event undoes {@code task_reserve_payment}.</li>
 *   <li><b>COMPENSATION Boundary</b> ({@code boundary_charge_compensation}):
 *       Links to {@code task_reverse_charge}. This handler fires only if this task
 *       had already <em>completed</em> when a later compensation throw occurs
 *       (e.g., a downstream task fails after charge succeeded).</li>
 * </ol>
 *
 * <p>Simulation:
 * <ul>
 *   <li>{@code amount > 10000} → throws {@code PAYMENT_FAILED} (INSUFFICIENT_FUNDS)</li>
 *   <li>{@code paymentMethod == "INVALID"} → throws {@code PAYMENT_FAILED} (INVALID_PAYMENT_METHOD)</li>
 *   <li>Otherwise → happy path, completes with transaction details</li>
 * </ul>
 *
 * <p>Output variables (happy path only):
 * <ul>
 *   <li>{@code transactionId}</li>
 *   <li>{@code chargedAmount}</li>
 *   <li>{@code chargeStatus} — always {@code "SUCCESS"} on this path</li>
 *   <li>{@code chargedAt}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChargePaymentWorker {

    private static final double MAX_CHARGE_AMOUNT = 10_000.0;

    @JobWorker(type = "payment.charge", autoComplete = false)
    public void chargePayment(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String reservationId,
            @Variable Double amount,
            @Variable String paymentMethod,
            @Variable String customerId) {

        log.info(
                "[ChargePayment] type={} key={} paymentId={} amount={} method={}",
                job.getType(),
                job.getKey(),
                paymentId,
                amount,
                paymentMethod);

        try {
            // ── Simulate INSUFFICIENT_FUNDS ─────────────────────────────────────
            if (amount != null && amount > MAX_CHARGE_AMOUNT) {
                log.warn(
                        "[ChargePayment] paymentId={} amount={} exceeds limit — throwing PAYMENT_FAILED",
                        paymentId,
                        amount);
                client.newThrowErrorCommand(job.getKey())
                        .errorCode("PAYMENT_FAILED")
                        .errorMessage(
                                "Insufficient funds for paymentId=" + paymentId + " amount=" + amount)
                        .send()
                        .join();
                return;
            }

            // ── Simulate INVALID_PAYMENT_METHOD ─────────────────────────────────
            if ("INVALID".equalsIgnoreCase(paymentMethod)) {
                log.warn(
                        "[ChargePayment] paymentId={} paymentMethod=INVALID — throwing PAYMENT_FAILED",
                        paymentId);
                client.newThrowErrorCommand(job.getKey())
                        .errorCode("PAYMENT_FAILED")
                        .errorMessage("Invalid payment method for paymentId=" + paymentId)
                        .send()
                        .join();
                return;
            }

            // ── Happy path ───────────────────────────────────────────────────────
            var transactionId =
                    "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            log.info(
                    "[ChargePayment] paymentId={} SUCCESS transactionId={}", paymentId, transactionId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "transactionId", transactionId,
                            "chargedAmount", amount,
                            "chargeStatus", "SUCCESS",
                            "chargedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            // Guard: only fail for unexpected exceptions (not the intentional BPMN errors above)
            if (ex.getMessage() != null && ex.getMessage().contains("PAYMENT_FAILED")) {
                throw ex;
            }
            log.error("[ChargePayment] Unexpected error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Unexpected error during payment charge: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
