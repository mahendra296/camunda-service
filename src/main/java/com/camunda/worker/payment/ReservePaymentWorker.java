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
 * Reserves the payment amount before charging.
 *
 * <p>A COMPENSATION BOUNDARY EVENT is attached to this task in the BPMN:
 * {@code boundary_reserve_compensation → task_reverse_reservation}.
 *
 * <p>If a compensation throw event fires later in the process (e.g., after the charge
 * task fails), Zeebe will invoke {@link ReversePaymentReservationWorker} to undo the
 * reservation made here.
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code reservationId} — unique ID for this hold</li>
 *   <li>{@code reservedAmount} — the amount that was reserved</li>
 *   <li>{@code reservedAt} — epoch millis when the reservation was made</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservePaymentWorker {

    @JobWorker(type = "payment.reserve", autoComplete = false)
    public void reservePayment(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable Double amount,
            @Variable String currency,
            @Variable String customerId) {

        log.info(
                "[ReservePayment] type={} key={} paymentId={} amount={} {}",
                job.getType(),
                job.getKey(),
                paymentId,
                amount,
                currency);

        try {
            var reservationId = "RESV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("[ReservePayment] paymentId={} reservationId={} reserved", paymentId, reservationId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "reservationId", reservationId,
                            "reservedAmount", amount,
                            "reservedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[ReservePayment] Unexpected error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Unexpected error during payment reservation: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
