package com.camunda.service;

import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserTaskService {

    private final JobClient jobClient;

    /**
     * Completes the "Approve Shipment" user task (assignee: warehouse-supervisor).
     * Sets shipmentApproved=true/false so downstream logic or workers can react.
     */
    public void completeShipmentApproval(long taskKey, boolean approved, String note) {
        log.info("[UserTaskService] Completing shipment approval taskKey={} approved={}", taskKey, approved);
        jobClient
                .newCompleteCommand(taskKey)
                .variables(Map.of("shipmentApproved", approved, "shipmentApprovalNote", note != null ? note : ""))
                .send()
                .join();
    }

    /**
     * Completes the "Approve Cancellation" user task (assignee: support-agent).
     * Sets cancellationApproved=true/false to drive the downstream cancellation path.
     */
    public void completeCancellationApproval(long taskKey, boolean approved, String reason) {
        log.info("[UserTaskService] Completing cancellation approval taskKey={} approved={}", taskKey, approved);
        jobClient
                .newCompleteCommand(taskKey)
                .variables(Map.of("cancellationApproved", approved, "cancellationReason", reason != null ? reason : ""))
                .send()
                .join();
    }

    /**
     * Completes the "Handle Delivery Issue" user task (assignee: support-agent).
     * Sets resolution="RESHIP" or "REFUND" to drive the gw_reship_or_refund gateway.
     */
    public void completeDeliveryIssue(long taskKey, String resolution, String issueDescription) {
        log.info("[UserTaskService] Completing delivery issue taskKey={} resolution={}", taskKey, resolution);
        jobClient
                .newCompleteCommand(taskKey)
                .variables(Map.of(
                        "resolution", resolution != null ? resolution : "RESHIP",
                        "issueDescription", issueDescription != null ? issueDescription : ""))
                .send()
                .join();
    }

    /**
     * Completes the "Initiate Refund" user task (assignee: finance-agent).
     * Captures refund amount, method, and a note before ProcessRefundWorker runs.
     */
    public void completeRefundInitiation(long taskKey, double refundAmount, String refundMethod, String note) {
        log.info(
                "[UserTaskService] Completing refund initiation taskKey={} amount={} method={}",
                taskKey,
                refundAmount,
                refundMethod);
        jobClient
                .newCompleteCommand(taskKey)
                .variables(Map.of(
                        "refundAmount",
                        refundAmount,
                        "refundMethod",
                        refundMethod != null ? refundMethod : "ORIGINAL_PAYMENT",
                        "refundNote",
                        note != null ? note : "",
                        "refundInitiatedAt",
                        System.currentTimeMillis()))
                .send()
                .join();
    }
}
