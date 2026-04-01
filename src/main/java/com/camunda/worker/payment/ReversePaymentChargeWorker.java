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
 * COMPENSATION HANDLER for {@code task_charge_payment}.
 *
 * <p>This worker is linked to {@code boundary_charge_compensation} via a compensation
 * association in the BPMN. It is declared with {@code isForCompensation="true"}, which
 * means it is <strong>not</strong> part of the normal sequence flow.
 *
 * <p><b>Important distinction from ReversePaymentReservationWorker:</b>
 * <ul>
 *   <li>In the <em>PAYMENT_FAILED error boundary path</em>: the error boundary is
 *       <em>interrupting</em>, so {@code task_charge_payment} is cancelled before it
 *       completes. Zeebe only compensates tasks that <em>completed</em>, so this
 *       handler is <strong>NOT</strong> triggered in that scenario.</li>
 *   <li>In a scenario where the charge task <em>succeeds</em> but a later task (e.g.,
 *       {@code task_send_payment_confirmation}) throws an error and triggers compensation,
 *       Zeebe WILL invoke this worker to issue a refund for the already-charged amount.</li>
 * </ul>
 *
 * <p>This worker therefore acts as the <strong>refund handler</strong> in multi-step
 * failure scenarios where the charge has already been captured.
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code chargeReversed} — {@code true}</li>
 *   <li>{@code chargeReversedAt} — epoch millis</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReversePaymentChargeWorker {

    @JobWorker(type = "payment.reverse-charge", autoComplete = false)
    public void reverseCharge(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable(optional = true) String transactionId,
            @Variable(optional = true) Double chargedAmount) {

        log.info(
                "[ReversePaymentCharge] COMPENSATION type={} key={} paymentId={} transactionId={}",
                job.getType(),
                job.getKey(),
                paymentId,
                transactionId);

        try {
            // Issue a full refund via payment gateway API
            log.info(
                    "[ReversePaymentCharge] Issuing refund for transactionId={} amount={}",
                    transactionId,
                    chargedAmount);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "chargeReversed", true,
                            "chargeReversedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[ReversePaymentCharge] Error reversing charge paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to reverse payment charge: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
