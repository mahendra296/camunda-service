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
public class PrepareShipmentWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.prepare-shipment", autoComplete = false)
    public void prepareShipment(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String shippingAddress,
            @Variable String reservationId) {

        try {
            log.info(
                    "[PrepareShipment] orderId={} address={}, variable: {}",
                    orderId,
                    shippingAddress,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            String trackingNumber =
                    "TRK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            String warehouseId = "WH-CENTRAL-01";

            // TODO: call warehouse management system
            log.info(
                    "[PrepareShipment] orderId={} trackingNumber={} warehouse={}",
                    orderId,
                    trackingNumber,
                    warehouseId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "trackingNumber",
                            trackingNumber,
                            "warehouseId",
                            warehouseId,
                            "shipmentPreparedAt",
                            System.currentTimeMillis(),
                            "estimatedDelivery",
                            System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[PrepareShipment] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
