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
 * COMPENSATION HANDLER for {@code task_reserve_payment}.
 *
 * <p>This worker is linked to {@code boundary_reserve_compensation} via a compensation
 * association in the BPMN. It is declared with {@code isForCompensation="true"}, which
 * means it is <strong>not</strong> part of the normal sequence flow — Zeebe invokes it
 * only when a compensation throw event fires and {@code task_reserve_payment} had previously
 * completed in the current process instance.
 *
 * <p>Compensation trigger scenario:
 * <ol>
 *   <li>{@code task_reserve_payment} completes → reservation held.</li>
 *   <li>{@code task_charge_payment} throws {@code PAYMENT_FAILED}.</li>
 *   <li>Error boundary catches it → {@code task_handle_payment_error} runs.</li>
 *   <li>{@code throw_compensation} fires → Zeebe sees that {@code task_reserve_payment}
 *       completed and calls THIS worker to undo the reservation.</li>
 * </ol>
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code reservationReversed} — {@code true}</li>
 *   <li>{@code reservationReversedAt} — epoch millis</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReversePaymentReservationWorker {

    @JobWorker(type = "payment.reverse-reservation", autoComplete = false)
    public void reverseReservation(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable(optional = true) String reservationId,
            @Variable(optional = true) Double reservedAmount) {

        log.info(
                "[ReversePaymentReservation] COMPENSATION type={} key={} paymentId={} reservationId={}",
                job.getType(),
                job.getKey(),
                paymentId,
                reservationId);

        try {
            // Undo the fund hold in the payment gateway / ledger
            log.info(
                    "[ReversePaymentReservation] Releasing reservation reservationId={} amount={}",
                    reservationId,
                    reservedAmount);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "reservationReversed", true,
                            "reservationReversedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error(
                    "[ReversePaymentReservation] Error reversing reservation paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to reverse payment reservation: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
