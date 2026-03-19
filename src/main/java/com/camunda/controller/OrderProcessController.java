package com.camunda.controller;

import com.camunda.dto.MessageRequest;
import com.camunda.dto.OrderRequest;
import com.camunda.dto.StartOrderResponse;
import com.camunda.service.OrderProcessService;
import jakarta.validation.Valid;
import java.util.HashMap;
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
     * POST /api/orders/{flowId}/start
     */
    @PostMapping("/{flowId}/start")
    public ResponseEntity<StartOrderResponse> startOrder(
            @PathVariable String flowId, @Valid @RequestBody OrderRequest request) {

        log.info("[API] POST /api/orders/{}/start orderId={}", flowId, request.getOrderId());
        StartOrderResponse response = orderProcessService.startOrderProcess(flowId, request);
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

    // ═══════════════════════════════════════════════════════════════
    // SHIPMENT EVENTS — one order can produce multiple shipments
    // (one per product line item, handled by multi-instance subprocess)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Warehouse signals that ALL product shipments for this order are ready for dispatch.
     * This triggers the parallel multi-instance Shipment Process (one instance per product).
     *
     * POST /api/orders/{orderId}/shipment-ready
     *
     * Optional body variables:
     *   { "variables": { "note": "...", "warehouseId": "..." } }
     */
    @PostMapping("/{orderId}/shipment-ready")
    public ResponseEntity<Map<String, Object>> shipmentReady(
            @PathVariable String orderId,
            @RequestBody(required = false) MessageRequest request) {

        log.info("[API] POST /api/orders/{}/shipment-ready — triggering per-product shipment subprocess", orderId);

        Map<String, Object> variables = request != null && request.getVariables() != null
                ? request.getVariables()
                : new HashMap<>();

        String note = (String) variables.getOrDefault("note", "");
        String warehouseId = (String) variables.getOrDefault("warehouseId", "");

        orderProcessService.sendShipmentReadyMessage(orderId, note, warehouseId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SHIPMENT_READY_SENT");
        response.put("orderId", orderId);
        response.put("message", "Shipment subprocess will run in parallel for each product in the order");
        if (!warehouseId.isEmpty()) {
            response.put("warehouseId", warehouseId);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Courier webhook: delivery confirmed or failed for a specific product shipment.
     * The trackingNumber identifies which product shipment is being confirmed.
     *
     * POST /api/orders/{orderId}/shipments/{trackingNumber}/delivery
     *
     * Query params:
     *   success  — true (delivered) / false (failed), default true
     *   note     — optional delivery note or failure reason
     */
    @PostMapping("/{orderId}/shipments/{trackingNumber}/delivery")
    public ResponseEntity<Map<String, Object>> productShipmentDelivery(
            @PathVariable String orderId,
            @PathVariable String trackingNumber,
            @RequestParam(defaultValue = "true") boolean success,
            @RequestParam(required = false) String note) {

        log.info("[API] POST /api/orders/{}/shipments/{}/delivery success={}", orderId, trackingNumber, success);
        orderProcessService.sendShipmentDeliveryUpdate(orderId, trackingNumber, success, note);

        Map<String, Object> response = new HashMap<>();
        response.put("status", success ? "DELIVERED" : "DELIVERY_FAILED");
        response.put("orderId", orderId);
        response.put("trackingNumber", trackingNumber);
        response.put("deliverySuccess", success);
        if (note != null && !note.isBlank()) {
            response.put("note", note);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Update the status of a specific product shipment (e.g., IN_TRANSIT, OUT_FOR_DELIVERY).
     * This is informational — it does not correlate a BPMN message, it just logs/tracks progress.
     *
     * POST /api/orders/{orderId}/shipments/{trackingNumber}/status
     *
     * Body: { "status": "IN_TRANSIT", "location": "Chicago Hub", "note": "..." }
     */
    @PostMapping("/{orderId}/shipments/{trackingNumber}/status")
    public ResponseEntity<Map<String, Object>> updateShipmentStatus(
            @PathVariable String orderId,
            @PathVariable String trackingNumber,
            @RequestBody Map<String, String> body) {

        String status = body.getOrDefault("status", "UNKNOWN");
        String location = body.getOrDefault("location", "");
        String note = body.getOrDefault("note", "");

        log.info("[API] POST /api/orders/{}/shipments/{}/status status={} location={}",
                orderId, trackingNumber, status, location);

        orderProcessService.updateShipmentStatus(orderId, trackingNumber, status, location, note);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SHIPMENT_STATUS_UPDATED");
        response.put("orderId", orderId);
        response.put("trackingNumber", trackingNumber);
        response.put("shipmentStatus", status);
        if (!location.isEmpty()) {
            response.put("location", location);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Courier webhook: all shipments delivered — confirm order completion.
     * POST /api/orders/{orderId}/delivery-confirmation
     *
     * Query params:
     *   success — true (all delivered) / false (one or more failed), default true
     *   note    — optional note
     */
    @PostMapping("/{orderId}/delivery-confirmation")
    public ResponseEntity<Map<String, String>> deliveryConfirmation(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "true") boolean success,
            @RequestParam(required = false) String note) {

        log.info("[API] POST /api/orders/{}/delivery-confirmation success={}", orderId, success);
        orderProcessService.sendDeliveryConfirmationMessage(orderId, success, note);
        return ResponseEntity.ok(Map.of(
                "status", "DELIVERY_CONFIRMATION_SENT",
                "orderId", orderId,
                "deliverySuccess", String.valueOf(success)));
    }
}
