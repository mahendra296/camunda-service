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
    // SHIPMENT EVENTS — two-tier message design:
    //   1. Order-level ShipmentReady   → unblocks the event-based gateway
    //   2. Per-item ItemShipmentReady  → unblocks each multi-instance subprocess
    // ═══════════════════════════════════════════════════════════════

    /**
     * Signals that all shipments for an order are ready to be dispatched.
     * Publishes the order-level ShipmentReady message (correlated by orderId)
     * which unblocks the event-based gateway and starts the Shipment Process subprocess.
     *
     * POST /api/orders/{orderId}/shipment-ready
     */
    @PostMapping("/{orderId}/shipment-ready")
    public ResponseEntity<Map<String, Object>> orderShipmentReady(@PathVariable String orderId) {
        log.info("[API] POST /api/orders/{}/shipment-ready", orderId);
        orderProcessService.sendShipmentReadyMessage(orderId);
        return ResponseEntity.ok(Map.of(
                "status", "SHIPMENT_READY_SENT",
                "orderId", orderId,
                "message", "Shipment Process subprocess triggered for order " + orderId));
    }

    /**
     * Warehouse signals that a specific product shipment is ready for dispatch.
     * Publishes the per-item ItemShipmentReady message (correlated by orderId_productId)
     * which unblocks the corresponding multi-instance subprocess instance.
     *
     * POST /api/orders/{orderId}/products/{productId}/shipment-ready
     *
     * Optional body variables:
     *   { "variables": { "note": "...", "warehouseId": "..." } }
     */
    @PostMapping("/{orderId}/products/{productId}/shipment-ready")
    public ResponseEntity<Map<String, Object>> itemShipmentReady(
            @PathVariable String orderId,
            @PathVariable String productId,
            @RequestBody(required = false) MessageRequest request) {

        log.info("[API] POST /api/orders/{}/products/{}/shipment-ready", orderId, productId);

        Map<String, Object> variables = request != null && request.getVariables() != null
                ? request.getVariables()
                : new HashMap<>();

        String note = (String) variables.getOrDefault("note", "");
        String warehouseId = (String) variables.getOrDefault("warehouseId", "");

        orderProcessService.sendItemShipmentReadyMessage(orderId, productId, note, warehouseId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ITEM_SHIPMENT_READY_SENT");
        response.put("orderId", orderId);
        response.put("productId", productId);
        response.put("message", "ItemShipmentReady message sent for product " + productId);
        if (!warehouseId.isEmpty()) {
            response.put("warehouseId", warehouseId);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Sends a shipment approval notification for an in-flight order.
     * Publishes a ShipmentApprovalNotification message correlated by orderId.
     *
     * POST /api/orders/{orderId}/shipment-approval/notify
     */
    @PostMapping("/{orderId}/shipment-approval/notify")
    public ResponseEntity<Map<String, String>> notifyShipmentApproval(@PathVariable String orderId) {
        log.info("[API] POST /api/orders/{}/shipment-approval/notify", orderId);
        orderProcessService.sendShipmentApprovalNotification(orderId);
        return ResponseEntity.ok(Map.of(
                "status", "SHIPMENT_APPROVAL_NOTIFICATION_SENT",
                "orderId", orderId));
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
