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
 * Fires from the INCLUSIVE GATEWAY split when {@code paymentMethod = "CARD"}.
 *
 * <p>Awards loyalty points proportional to the charged amount.
 * The {@code gw_inclusive_join} waits for this task only if the inclusive split
 * activated it (i.e. the condition {@code =paymentMethod = "CARD"} was true).
 *
 * <p>Output variables:
 * <ul>
 *   <li>{@code loyaltyPointsAwarded} — points credited (amount / 10, rounded)</li>
 *   <li>{@code loyaltyUpdatedAt} — epoch millis</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateLoyaltyPointsWorker {

    @JobWorker(type = "payment.update-loyalty-points", autoComplete = false)
    public void updateLoyaltyPoints(
            JobClient client,
            ActivatedJob job,
            @Variable String paymentId,
            @Variable String customerId,
            @Variable Double amount,
            @Variable(optional = true) String transactionId) {

        log.info(
                "[UpdateLoyaltyPoints] INCLUSIVE-BRANCH type={} key={} paymentId={} amount={}",
                job.getType(),
                job.getKey(),
                paymentId,
                amount);

        try {
            long pointsAwarded = Math.round(amount / 10.0);
            log.info(
                    "[UpdateLoyaltyPoints] Awarding {} loyalty points to customerId={} for paymentId={}",
                    pointsAwarded,
                    customerId,
                    paymentId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "loyaltyPointsAwarded", pointsAwarded, "loyaltyUpdatedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception ex) {
            log.error("[UpdateLoyaltyPoints] Error paymentId={}", paymentId, ex);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage("Failed to update loyalty points: " + ex.getMessage())
                    .send()
                    .join();
        }
    }
}
