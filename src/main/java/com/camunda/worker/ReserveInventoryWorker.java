package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReserveInventoryWorker {

    @JobWorker(type = "order.reserve-inventory")
    public Map<String, Object> reserveInventory(@Variable String orderId, @Variable List<Map<String, Object>> items) {

        log.info("[ReserveInventory] orderId={}", orderId);

        String reservationId =
                "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // TODO: call inventory service to reserve stock
        log.info("[ReserveInventory] Reserved inventory with reservationId={} for orderId={}", reservationId, orderId);

        return Map.of(
                "reservationId",
                reservationId,
                "reservedAt",
                System.currentTimeMillis(),
                "reservedItemCount",
                items != null ? items.size() : 0);
    }
}
