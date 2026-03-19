package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchCarrierWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "shipment.dispatch", autoComplete = false)
    public void dispatchToCarrier(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String packageId,
            @Variable String trackingNumber,
            @Variable String carrierId,
            @Variable String carrierName,
            @Variable String carrierCode,
            @Variable String serviceLevel,
            @Variable String labelUrl,
            @Variable String shippingAddress,
            @Variable(name = "currentItem") Map<String, Object> currentItem) {

        String productId = currentItem != null ? String.valueOf(currentItem.get("productId")) : "UNKNOWN";
        String productName = currentItem != null ? String.valueOf(currentItem.get("productName")) : "UNKNOWN";

        try {
            log.info(
                    "[DispatchCarrier] orderId={} productId={} package={} tracking={} carrier={} variable: {}",
                    orderId,
                    productId,
                    packageId,
                    trackingNumber,
                    carrierName,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("[DispatchCarrier] Error serializing variables", e);
        }

        try {
            // TODO: call courier pickup API to schedule collection from warehouse
            String dispatchReference = "DISP-" + trackingNumber;
            String trackingUrl = "https://track.shipments.example.com/" + trackingNumber;
            long dispatchedAt = System.currentTimeMillis();

            log.info(
                    "[DispatchCarrier] Handed off to carrier. orderId={} productId={} carrier={} tracking={} dispatchRef={}",
                    orderId,
                    productId,
                    carrierName,
                    trackingNumber,
                    dispatchReference);

            // shipmentResult is collected by the multi-instance loop into the process-level "shipments" array
            Map<String, Object> shipmentResult = new HashMap<>();
            shipmentResult.put("productId", productId);
            shipmentResult.put("productName", productName);
            shipmentResult.put("trackingNumber", trackingNumber);
            shipmentResult.put("packageId", packageId);
            shipmentResult.put("carrierId", carrierId);
            shipmentResult.put("carrierName", carrierName);
            shipmentResult.put("carrierCode", carrierCode);
            shipmentResult.put("serviceLevel", serviceLevel);
            shipmentResult.put("dispatchReference", dispatchReference);
            shipmentResult.put("trackingUrl", trackingUrl);
            shipmentResult.put("labelUrl", labelUrl != null ? labelUrl : "");
            shipmentResult.put("status", "DISPATCHED");
            shipmentResult.put("dispatchedAt", dispatchedAt);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "dispatchReference", dispatchReference,
                            "trackingUrl", trackingUrl,
                            "shipmentStatus", "DISPATCHED",
                            "dispatchedAt", dispatchedAt,
                            "shipmentResult", shipmentResult))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[DispatchCarrier] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
