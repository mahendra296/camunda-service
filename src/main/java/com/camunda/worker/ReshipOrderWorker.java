package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReshipOrderWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.reship", autoComplete = false)
    public void reshipOrder(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String shippingAddress,
            @Variable String customerEmail) {

        try {
            log.info(
                    "[ReshipOrder] Creating reship for orderId={}, variable: {}",
                    orderId,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            String newTrackingNumber =
                    "TRK-RE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // TODO: create new shipment in warehouse system
            log.info("[ReshipOrder] Reship dispatched. orderId={} newTracking={}", orderId, newTrackingNumber);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "reshipTrackingNumber",
                            newTrackingNumber,
                            "reshipInitiatedAt",
                            System.currentTimeMillis(),
                            "reshipStatus",
                            "DISPATCHED",
                            "customerNotified",
                            true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[ReshipOrder] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
