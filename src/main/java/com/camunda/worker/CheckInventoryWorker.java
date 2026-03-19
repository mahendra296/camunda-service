package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInventoryWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.check-inventory", autoComplete = false)
    public void checkInventory(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable List<Map<String, Object>> items) {

        try {
            log.info("[CheckInventory] orderId={}, variable: {}", orderId, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: call real inventory service
            boolean inStock = true;
            List<String> outOfStockItems = new ArrayList<>();

            if (items != null) {
                for (Map<String, Object> item : items) {
                    String productId = (String) item.get("productId");
                    Number qty = (Number) item.get("quantity");
                    // Simulate: products with "OUT_" prefix are out of stock
                    if (productId != null && productId.startsWith("OUT_")) {
                        inStock = false;
                        outOfStockItems.add(productId);
                    }
                    log.debug("[CheckInventory] productId={} qty={}", productId, qty);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("inStock", inStock);
            result.put("outOfStockItems", outOfStockItems);
            result.put("inventoryCheckedAt", System.currentTimeMillis());

            log.info("[CheckInventory] orderId={} inStock={} outOfStock={}", orderId, inStock, outOfStockItems);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CheckInventory] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
