package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ShipOrderWorker {

    @JobWorker(type = "order.ship")
    public Map<String, Object> shipOrder(
            @Variable String orderId,
            @Variable String trackingNumber,
            @Variable String warehouseId,
            @Variable String shippingAddress) {

        log.info("[ShipOrder] orderId={} tracking={} from={}", orderId, trackingNumber, warehouseId);

        // TODO: call courier/logistics API to dispatch
        log.info(
                "[ShipOrder] Order dispatched. orderId={} tracking={} to={}", orderId, trackingNumber, shippingAddress);

        return Map.of(
                "shippedAt",
                System.currentTimeMillis(),
                "carrierName",
                "FastShip Express",
                "shipmentStatus",
                "DISPATCHED",
                "trackingUrl",
                "https://track.fastship.example.com/" + trackingNumber);
    }
}
