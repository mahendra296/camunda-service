package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReserveInventoryWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.reserve-inventory", autoComplete = false)
    public void reserveInventory(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable List<Map<String, Object>> items) {

        try {
            log.info("[ReserveInventory] orderId={}, variable: {}", orderId, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            String reservationId =
                    "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // TODO: call inventory service to reserve stock
            log.info("[ReserveInventory] Reserved inventory with reservationId={} for orderId={}", reservationId, orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "reservationId", reservationId,
                            "reservedAt", System.currentTimeMillis(),
                            "reservedItemCount", items != null ? items.size() : 0))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[ReserveInventory] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
