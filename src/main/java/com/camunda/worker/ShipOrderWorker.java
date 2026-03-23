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
public class ShipOrderWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.ship", autoComplete = false)
    public void shipOrder(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String trackingNumber,
            @Variable String warehouseId,
            @Variable String shippingAddress) {

        try {
            log.info(
                    "[ShipOrder] orderId={} tracking={} from={}, variable: {}",
                    orderId,
                    trackingNumber,
                    warehouseId,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: call courier/logistics API to dispatch
            log.info(
                    "[ShipOrder] Order dispatched. orderId={} tracking={} to={}",
                    orderId,
                    trackingNumber,
                    shippingAddress);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "shippedAt",
                            System.currentTimeMillis(),
                            "carrierName",
                            "FastShip Express",
                            "shipmentStatus",
                            "DISPATCHED",
                            "trackingUrl",
                            "https://track.fastship.example.com/" + trackingNumber))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[ShipOrder] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
