package com.camunda.service;

import com.camunda.dto.OrderItemDto;
import com.camunda.dto.OrderRequest;
import com.camunda.dto.StartOrderResponse;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
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

    private final ZeebeClient zeebeClient;

    public StartOrderResponse startOrderProcess(OrderRequest request) {
        log.info("[OrderProcessService] Starting order process for orderId={}", request.getOrderId());

        Map<String, Object> variables = buildVariables(request);

        PublishMessageResponse response = zeebeClient
                .newPublishMessageCommand()
                .messageName("OrderReceived")
                .correlationKey(request.getOrderId())
                .variables(variables)
                .send()
                .join();

        log.info(
                "[OrderProcessService] Process started via message. messageKey={} orderId={}",
                response.getMessageKey(),
                request.getOrderId());

        return new StartOrderResponse(
                request.getOrderId(),
                response.getMessageKey(),
                "STARTED",
                "Order management process initiated successfully");
    }

    public void sendCancellationMessage(String orderId, String reason) {
        log.info("[OrderProcessService] Sending cancellation message for orderId={}", orderId);
        zeebeClient
                .newPublishMessageCommand()
                .messageName("OrderCancellation")
                .correlationKey(orderId)
                .variables(Map.of("cancellationReason", reason))
                .send()
                .join();
    }

    public void sendShipmentReadyMessage(String orderId, String shipmentNote) {
        log.info("[OrderProcessService] Sending ShipmentReady message for orderId={}", orderId);
        zeebeClient
                .newPublishMessageCommand()
                .messageName("ShipmentReady")
                .correlationKey(orderId)
                .variables(Map.of("shipmentNote", shipmentNote != null ? shipmentNote : ""))
                .send()
                .join();
    }

    public void sendDeliveryConfirmationMessage(String orderId, boolean deliverySuccess, String note) {
        log.info(
                "[OrderProcessService] Sending DeliveryConfirmation message for orderId={} success={}",
                orderId,
                deliverySuccess);
        zeebeClient
                .newPublishMessageCommand()
                .messageName("DeliveryConfirmation")
                .correlationKey(orderId)
                .variables(Map.of("deliverySuccess", deliverySuccess, "deliveryNote", note != null ? note : ""))
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
