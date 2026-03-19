package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReshipOrderWorker {

    @JobWorker(type = "order.reship")
    public Map<String, Object> reshipOrder(
            @Variable String orderId, @Variable String shippingAddress, @Variable String customerEmail) {

        log.info("[ReshipOrder] Creating reship for orderId={}", orderId);

        String newTrackingNumber =
                "TRK-RE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // TODO: create new shipment in warehouse system
        log.info("[ReshipOrder] Reship dispatched. orderId={} newTracking={}", orderId, newTrackingNumber);

        return Map.of(
                "reshipTrackingNumber",
                newTrackingNumber,
                "reshipInitiatedAt",
                System.currentTimeMillis(),
                "reshipStatus",
                "DISPATCHED",
                "customerNotified",
                true);
    }
}
