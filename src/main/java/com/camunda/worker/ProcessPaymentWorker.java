package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessPaymentWorker {

    private final ObjectMapper objectMapper;

    /**
     * autoComplete = false — we drive all job outcomes manually:
     *   newThrowErrorCommand  →  BPMN error (replaces deprecated ZeebeBpmnError)
     *   newCompleteCommand    →  success / decline result
     *   newFailCommand        →  retry on transient failure
     */
    @JobWorker(type = "order.process-payment", autoComplete = false)
    public void processPayment(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable double totalAmount,
            @Variable String paymentMethod,
            @Variable(optional = true) String paymentToken) {
        try {
            log.info("[ProcessPayment] orderId={} amount={} method={}, variable: {}", orderId, totalAmount, paymentMethod, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        // ── Missing / invalid token → BPMN error caught by boundary event ──────
        if (paymentToken == null || paymentToken.isBlank()) {
            log.warn("[ProcessPayment] orderId={} — missing payment token, throwing BPMN error", orderId);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("PAYMENT_FAILED")
                    .errorMessage("Payment token is missing or invalid for order: " + orderId)
                    .send()
                    .join();
            return;
        }

        // ── Simulate declined card (token starts with "FAIL_") ──────────────────
        if (paymentToken.startsWith("FAIL_")) {
            log.warn("[ProcessPayment] orderId={} — payment declined by gateway", orderId);
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "paymentStatus",      "DECLINED",
                            "paymentDeclineCode", "INSUFFICIENT_FUNDS",
                            "paymentProcessedAt", System.currentTimeMillis()))
                    .send()
                    .join();
            return;
        }

        // ── Happy path ───────────────────────────────────────────────────────────
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("[ProcessPayment] orderId={} SUCCESS transactionId={}", orderId, transactionId);

        client.newCompleteCommand(job.getKey())
                .variables(Map.of(
                        "paymentStatus",      "SUCCESS",
                        "transactionId",      transactionId,
                        "paymentProcessedAt", System.currentTimeMillis(),
                        "paidAmount",         totalAmount))
                .send()
                .join();
    }
}
