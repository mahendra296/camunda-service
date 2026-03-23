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
public class PickPackWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "shipment.pick-pack", autoComplete = false)
    public void pickAndPack(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String warehouseId,
            @Variable String reservationId,
            @Variable(name = "currentItem") Map<String, Object> currentItem) {

        String productId = currentItem != null ? String.valueOf(currentItem.get("productId")) : "UNKNOWN";
        int quantity = currentItem != null && currentItem.get("quantity") instanceof Number n ? n.intValue() : 1;

        try {
            log.info(
                    "[PickPack] orderId={} productId={} warehouse={} reservation={} variable: {}",
                    orderId,
                    productId,
                    warehouseId,
                    reservationId,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("[PickPack] Error serializing variables", e);
        }

        try {
            // TODO: call warehouse management system to trigger pick-pack workflow for the specific product
            String packageId = "PKG-"
                    + productId.substring(Math.max(0, productId.length() - 6)).toUpperCase() + "-"
                    + System.currentTimeMillis() % 10000;

            log.info(
                    "[PickPack] Product picked and packed. orderId={} productId={} packageId={} qty={}",
                    orderId,
                    productId,
                    packageId,
                    quantity);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "packageId",
                            packageId,
                            "itemCount",
                            1,
                            "totalQuantity",
                            quantity,
                            "packageStatus",
                            "PACKED",
                            "packedAt",
                            System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[PickPack] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
