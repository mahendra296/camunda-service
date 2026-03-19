package com.camunda.controller;

import com.camunda.dto.MessageRequest;
import com.camunda.dto.OrderRequest;
import com.camunda.dto.StartOrderResponse;
import com.camunda.service.OrderProcessService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderProcessController {

    private final OrderProcessService orderProcessService;

    /**
     * Start a new Order Management process instance.
     * POST /api/orders/start
     */
    @PostMapping("/start")
    public ResponseEntity<StartOrderResponse> startOrder(@Valid @RequestBody OrderRequest request) {
        log.info("[API] POST /api/orders/start orderId={}", request.getOrderId());
        StartOrderResponse response = orderProcessService.startOrderProcess(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Customer cancels an in-flight order.
     * POST /api/orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, String>> cancelOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Customer requested cancellation") String reason) {

        log.info("[API] POST /api/orders/{}/cancel reason={}", orderId, reason);
        orderProcessService.sendCancellationMessage(orderId, reason);
        return ResponseEntity.ok(Map.of("status", "CANCELLATION_SENT", "orderId", orderId));
    }

    /**
     * Warehouse signals that shipment is ready for dispatch.
     * POST /api/orders/{orderId}/shipment-ready
     */
    @PostMapping("/{orderId}/shipment-ready")
    public ResponseEntity<Map<String, String>> shipmentReady(
            @PathVariable String orderId, @RequestBody MessageRequest request) {

        log.info("[API] POST /api/orders/{}/shipment-ready", orderId);
        String note =
                request.getVariables() != null ? (String) request.getVariables().getOrDefault("note", "") : "";
        orderProcessService.sendShipmentReadyMessage(orderId, note);
        return ResponseEntity.ok(Map.of("status", "SHIPMENT_READY_SENT", "orderId", orderId));
    }

    /**
     * Courier webhook: delivery confirmed or failed.
     * POST /api/orders/{orderId}/delivery-confirmation
     */
    @PostMapping("/{orderId}/delivery-confirmation")
    public ResponseEntity<Map<String, String>> deliveryConfirmation(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "true") boolean success,
            @RequestParam(required = false) String note) {

        log.info("[API] POST /api/orders/{}/delivery-confirmation success={}", orderId, success);
        orderProcessService.sendDeliveryConfirmationMessage(orderId, success, note);
        return ResponseEntity.ok(Map.of("status", "DELIVERY_CONFIRMATION_SENT", "orderId", orderId));
    }
}
