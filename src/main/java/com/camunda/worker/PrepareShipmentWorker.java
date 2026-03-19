package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PrepareShipmentWorker {

    @JobWorker(type = "order.prepare-shipment")
    public Map<String, Object> prepareShipment(
            @Variable String orderId, @Variable String shippingAddress, @Variable String reservationId) {

        log.info("[PrepareShipment] orderId={} address={}", orderId, shippingAddress);

        String trackingNumber =
                "TRK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        String warehouseId = "WH-CENTRAL-01";

        // TODO: call warehouse management system
        log.info("[PrepareShipment] orderId={} trackingNumber={} warehouse={}", orderId, trackingNumber, warehouseId);

        return Map.of(
                "trackingNumber",
                trackingNumber,
                "warehouseId",
                warehouseId,
                "shipmentPreparedAt",
                System.currentTimeMillis(),
                "estimatedDelivery",
                System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L);
    }
}
