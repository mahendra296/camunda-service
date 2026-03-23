package com.camunda.controller;

import com.camunda.service.UserTaskService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class UserTaskController {

    private final UserTaskService userTaskService;

    /**
     * Complete the "Approve Shipment" user task (warehouse-supervisor).
     *
     * POST /api/tasks/{taskKey}/shipment-approval
     *
     * Body:
     *   { "approved": true, "note": "All items verified" }
     */
    @PostMapping("/{taskKey}/shipment-approval")
    public ResponseEntity<Map<String, Object>> approveShipment(
            @PathVariable long taskKey, @RequestBody Map<String, Object> body) {

        var approved = Boolean.TRUE.equals(body.get("approved"));
        var note = (String) body.getOrDefault("note", "");

        log.info("[API] POST /api/tasks/{}/shipment-approval approved={}", taskKey, approved);
        userTaskService.completeShipmentApproval(taskKey, approved, note);

        return ResponseEntity.ok(Map.of(
                "status", "SHIPMENT_APPROVAL_COMPLETED",
                "taskKey", taskKey,
                "approved", approved));
    }

    /**
     * Complete the "Approve Cancellation" user task (support-agent).
     *
     * POST /api/tasks/{taskKey}/cancellation-approval
     *
     * Body:
     *   { "approved": true, "reason": "Customer request confirmed" }
     */
    @PostMapping("/{taskKey}/cancellation-approval")
    public ResponseEntity<Map<String, Object>> approveCancellation(
            @PathVariable long taskKey, @RequestBody Map<String, Object> body) {

        var approved = Boolean.TRUE.equals(body.get("approved"));
        var reason = (String) body.getOrDefault("reason", "");

        log.info("[API] POST /api/tasks/{}/cancellation-approval approved={}", taskKey, approved);
        userTaskService.completeCancellationApproval(taskKey, approved, reason);

        return ResponseEntity.ok(Map.of(
                "status", "CANCELLATION_APPROVAL_COMPLETED",
                "taskKey", taskKey,
                "approved", approved));
    }

    /**
     * Complete the "Initiate Refund" user task (finance-agent).
     *
     * POST /api/tasks/{taskKey}/refund-initiation
     *
     * Body:
     *   { "refundAmount": 199.99, "refundMethod": "CREDIT_CARD", "note": "Full refund approved" }
     */
    @PostMapping("/{taskKey}/refund-initiation")
    public ResponseEntity<Map<String, Object>> initiateRefund(
            @PathVariable long taskKey, @RequestBody Map<String, Object> body) {

        var refundAmount = body.containsKey("refundAmount") ? ((Number) body.get("refundAmount")).doubleValue() : 0.0;
        var refundMethod = (String) body.getOrDefault("refundMethod", "ORIGINAL_PAYMENT");
        var note = (String) body.getOrDefault("note", "");

        log.info("[API] POST /api/tasks/{}/refund-initiation amount={} method={}", taskKey, refundAmount, refundMethod);
        userTaskService.completeRefundInitiation(taskKey, refundAmount, refundMethod, note);

        return ResponseEntity.ok(Map.of(
                "status", "REFUND_INITIATION_COMPLETED",
                "taskKey", taskKey,
                "refundAmount", refundAmount,
                "refundMethod", refundMethod));
    }

    /**
     * Complete the "Handle Delivery Issue" user task (support-agent).
     *
     * POST /api/tasks/{taskKey}/delivery-issue
     *
     * Body:
     *   { "resolution": "RESHIP", "issueDescription": "Package damaged in transit" }
     *
     * resolution values:
     *   "RESHIP"  → triggers task_reship_order (default)
     *   "REFUND"  → triggers task_initiate_refund (finance-agent user task)
     */
    @PostMapping("/{taskKey}/delivery-issue")
    public ResponseEntity<Map<String, Object>> handleDeliveryIssue(
            @PathVariable long taskKey, @RequestBody Map<String, Object> body) {

        var resolution = (String) body.getOrDefault("resolution", "RESHIP");
        var issueDescription = (String) body.getOrDefault("issueDescription", "");

        log.info("[API] POST /api/tasks/{}/delivery-issue resolution={}", taskKey, resolution);
        userTaskService.completeDeliveryIssue(taskKey, resolution, issueDescription);

        return ResponseEntity.ok(Map.of(
                "status", "DELIVERY_ISSUE_COMPLETED",
                "taskKey", taskKey,
                "resolution", resolution));
    }
}
