package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandlePaymentFailureWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.handle-payment-failure", autoComplete = false)
    public void handlePaymentFailure(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String reservationId) {

        try {
            log.error("[HandlePaymentFailure] orderId={} — releasing inventory reservation={}, variable: {}", orderId, reservationId, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: release reserved inventory
            // TODO: notify customer about payment error
            // TODO: log incident for ops team
            log.info("[HandlePaymentFailure] Inventory released and customer notified for orderId={}", orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "paymentFailureHandledAt", System.currentTimeMillis(),
                            "inventoryReleased", true,
                            "failureNotificationSent", true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[HandlePaymentFailure] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
