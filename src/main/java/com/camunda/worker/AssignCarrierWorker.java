package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssignCarrierWorker {

    private static final List<Map<String, String>> CARRIERS = List.of(
            Map.of("name", "FastShip Express", "code", "FSX", "service", "STANDARD"),
            Map.of("name", "QuickDeliver Co", "code", "QDC", "service", "EXPRESS"),
            Map.of("name", "ReliableCargo Ltd", "code", "RCL", "service", "ECONOMY"));

    private final ObjectMapper objectMapper;

    @JobWorker(type = "shipment.assign-carrier", autoComplete = false)
    public void assignCarrier(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String shippingAddress,
            @Variable(name = "totalAmount") Double totalAmount,
            @Variable(name = "currentItem") Map<String, Object> currentItem) {

        try {
            log.info(
                    "[AssignCarrier] orderId={} address={} productId={} variable: {}",
                    orderId,
                    shippingAddress,
                    currentItem != null ? currentItem.get("productId") : "N/A",
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("[AssignCarrier] Error serializing variables", e);
        }

        try {
            // Select carrier based on order value: EXPRESS for high-value, STANDARD otherwise
            // TODO: integrate with carrier selection / rate-shopping API
            Map<String, String> carrier = totalAmount != null && totalAmount > 500
                    ? CARRIERS.get(1) // QuickDeliver EXPRESS
                    : CARRIERS.get(0); // FastShip STANDARD

            String carrierId = "CAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            String productId = currentItem != null ? String.valueOf(currentItem.get("productId")) : "UNKNOWN";
            log.info(
                    "[AssignCarrier] Carrier selected. orderId={} productId={} carrier={} service={}",
                    orderId,
                    productId,
                    carrier.get("name"),
                    carrier.get("service"));

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "carrierId", carrierId,
                            "carrierName", carrier.get("name"),
                            "carrierCode", carrier.get("code"),
                            "serviceLevel", carrier.get("service"),
                            "carrierAssignedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[AssignCarrier] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
