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
public class GenerateShippingLabelWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "shipment.generate-label", autoComplete = false)
    public void generateShippingLabel(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String carrierId,
            @Variable String carrierCode,
            @Variable String serviceLevel,
            @Variable String shippingAddress,
            @Variable(name = "currentItem") Map<String, Object> currentItem) {

        String productId = currentItem != null ? String.valueOf(currentItem.get("productId")) : "UNKNOWN";

        try {
            log.info(
                    "[GenerateLabel] orderId={} productId={} carrier={} service={} variable: {}",
                    orderId,
                    productId,
                    carrierCode,
                    serviceLevel,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("[GenerateLabel] Error serializing variables", e);
        }

        try {
            // TODO: call carrier label generation API (e.g. EasyPost, ShipStation)
            // Tracking number includes productId to ensure uniqueness per product shipment
            String trackingNumber = carrierCode + "-"
                    + productId.substring(Math.max(0, productId.length() - 6)).toUpperCase() + "-"
                    + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String labelId =
                    "LBL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String labelUrl = "https://labels.shipments.example.com/" + labelId + ".pdf";
            long estimatedDeliveryMs = System.currentTimeMillis()
                    + ("EXPRESS".equals(serviceLevel) ? 1 : "ECONOMY".equals(serviceLevel) ? 5 : 3)
                            * 24L
                            * 60
                            * 60
                            * 1000;

            log.info(
                    "[GenerateLabel] Label generated. orderId={} productId={} tracking={} labelId={}",
                    orderId,
                    productId,
                    trackingNumber,
                    labelId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "trackingNumber", trackingNumber,
                            "labelId", labelId,
                            "labelUrl", labelUrl,
                            "estimatedDeliveryMs", estimatedDeliveryMs,
                            "labelGeneratedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[GenerateLabel] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
