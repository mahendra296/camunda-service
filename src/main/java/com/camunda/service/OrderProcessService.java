package com.camunda.service;

import com.camunda.dto.OrderItemDto;
import com.camunda.dto.OrderRequest;
import com.camunda.dto.StartOrderResponse;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessService {

    private final CamundaClient camundaClient;

    public StartOrderResponse startOrderProcess(String flowId, OrderRequest request) {
        log.info("[OrderProcessService] Starting order process for orderId={}", request.getOrderId());

        Map<String, Object> variables = buildVariables(request);

        ProcessInstanceEvent response = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId(flowId)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        log.info(
                "[OrderProcessService] Process started via message. messageKey={} orderId={}",
                response.getProcessInstanceKey(),
                request.getOrderId());

        return new StartOrderResponse(
                request.getOrderId(),
                response.getProcessDefinitionKey(),
                "STARTED",
                "Order management process initiated successfully");
    }

    public void sendCancellationMessage(String orderId, String reason) {
        log.info("[OrderProcessService] Sending cancellation message for orderId={}", orderId);
        camundaClient
                .newPublishMessageCommand()
                .messageName("OrderCancellation")
                .correlationKey(orderId)
                .variables(Map.of("cancellationReason", reason))
                .send()
                .join();
    }

    /**
     * Fires the ShipmentReady BPMN message, which triggers the parallel multi-instance
     * Shipment Process — one subprocess instance per product in the order's items list.
     */
    public void sendShipmentReadyMessage(String orderId, String shipmentNote, String warehouseId) {
        log.info("[OrderProcessService] Sending ShipmentReady message for orderId={} warehouseId={}",
                orderId, warehouseId);

        Map<String, Object> vars = new HashMap<>();
        vars.put("shipmentNote", shipmentNote != null ? shipmentNote : "");
        vars.put("warehouseId", warehouseId != null ? warehouseId : "");
        vars.put("shipmentReadyAt", System.currentTimeMillis());

        camundaClient
                .newPublishMessageCommand()
                .messageName("ShipmentReady")
                .correlationKey(orderId)
                .variables(vars)
                .send()
                .join();
    }

    /**
     * Sends a per-product shipment delivery update.
     * The trackingNumber is included as a process variable so downstream workers/
     * listeners can identify which product shipment was delivered.
     * Correlates via orderId (the BPMN DeliveryConfirmation message catch event).
     */
    public void sendShipmentDeliveryUpdate(String orderId, String trackingNumber, boolean success, String note) {
        log.info("[OrderProcessService] Sending shipment delivery update for orderId={} tracking={} success={}",
                orderId, trackingNumber, success);

        Map<String, Object> vars = new HashMap<>();
        vars.put("deliverySuccess", success);
        vars.put("trackingNumber", trackingNumber);
        vars.put("deliveryNote", note != null ? note : "");
        vars.put("deliveredAt", System.currentTimeMillis());

        camundaClient
                .newPublishMessageCommand()
                .messageName("DeliveryConfirmation")
                .correlationKey(orderId)
                .variables(vars)
                .send()
                .join();
    }

    /**
     * Logs a mid-transit status update for a specific product shipment.
     * Publishes a ShipmentStatusUpdate message so interested listeners can react
     * (e.g., notify the customer of IN_TRANSIT or OUT_FOR_DELIVERY events).
     */
    public void updateShipmentStatus(String orderId, String trackingNumber, String status, String location, String note) {
        log.info("[OrderProcessService] Updating shipment status for orderId={} tracking={} status={} location={}",
                orderId, trackingNumber, status, location);

        Map<String, Object> vars = new HashMap<>();
        vars.put("trackingNumber", trackingNumber);
        vars.put("shipmentStatus", status);
        vars.put("shipmentLocation", location != null ? location : "");
        vars.put("statusNote", note != null ? note : "");
        vars.put("statusUpdatedAt", System.currentTimeMillis());

        camundaClient
                .newPublishMessageCommand()
                .messageName("ShipmentStatusUpdate")
                .correlationKey(orderId)
                .variables(vars)
                .send()
                .join();
    }

    public void sendDeliveryConfirmationMessage(String orderId, boolean deliverySuccess, String note) {
        log.info("[OrderProcessService] Sending DeliveryConfirmation message for orderId={} success={}",
                orderId, deliverySuccess);

        Map<String, Object> vars = new HashMap<>();
        vars.put("deliverySuccess", deliverySuccess);
        vars.put("deliveryNote", note != null ? note : "");
        vars.put("deliveredAt", System.currentTimeMillis());

        camundaClient
                .newPublishMessageCommand()
                .messageName("DeliveryConfirmation")
                .correlationKey(orderId)
                .variables(vars)
                .send()
                .join();
    }

    // ──────────────────────────────────────────────
    private Map<String, Object> buildVariables(OrderRequest request) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", request.getOrderId());
        vars.put("customerId", request.getCustomerId());
        vars.put("customerEmail", request.getCustomerEmail());
        vars.put("customerName", request.getCustomerName());
        vars.put("shippingAddress", request.getShippingAddress());
        vars.put("paymentMethod", request.getPaymentMethod());
        vars.put("paymentToken", request.getPaymentToken());
        vars.put("items", toMapList(request.getItems()));
        return vars;
    }

    private List<Map<String, Object>> toMapList(List<OrderItemDto> items) {
        if (items == null) return List.of();
        return items.stream()
                .map(item -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", item.getProductId());
                    m.put("productName", item.getProductName());
                    m.put("quantity", item.getQuantity());
                    m.put("unitPrice", item.getUnitPrice());
                    return m;
                })
                .toList();
    }
}
