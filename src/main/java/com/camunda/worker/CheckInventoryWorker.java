package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CheckInventoryWorker {

    @JobWorker(type = "order.check-inventory")
    public Map<String, Object> checkInventory(@Variable String orderId, @Variable List<Map<String, Object>> items) {

        log.info("[CheckInventory] orderId={}", orderId);

        // TODO: call real inventory service
        boolean inStock = true;
        List<String> outOfStockItems = new java.util.ArrayList<>();

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
        return result;
    }
}
