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
public class NotifyShipmentApprovalWorker {

    private final ObjectMapper objectMapper;

    /**
     * Runs inside the multi-instance subprocess — one instance per order item.
     * Variables available per instance:
     *   - currentItem   : the item object from the `items` collection (productId, productName, quantity, unitPrice)
     *   - warehouseId   : set by the ShipmentReady message sent by the warehouse
     *   - shipmentNote  : optional note from the ShipmentReady message
     *   - orderId       : inherited from the parent process
     *   - shippingAddress: inherited from the parent process
     */
    @JobWorker(type = "order.notify-shipment-approval", autoComplete = false)
    public void notifyShipmentApproval(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable Map<String, Object> currentItem,
            @Variable String shippingAddress,
            @Variable String warehouseId,
            @Variable String shipmentNote) {

        var productId = (String) currentItem.get("productId");
        var productName = (String) currentItem.get("productName");

        try {
            log.info(
                    "[NotifyShipmentApproval] orderId={} productId={} productName={} warehouseId={}, variable: {}",
                    orderId,
                    productId,
                    productName,
                    warehouseId,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: send approval notification to warehouse-supervisor (e.g. email / push)
            log.info(
                    "[NotifyShipmentApproval] Notified warehouse-supervisor for orderId={} productId={} productName={} warehouseId={} address={} note={}",
                    orderId,
                    productId,
                    productName,
                    warehouseId,
                    shippingAddress,
                    shipmentNote);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "shipmentApprovalNotifiedAt", System.currentTimeMillis(), "approvalProductId", productId))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[NotifyShipmentApproval] Failed for orderId={} productId={}", orderId, productId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
