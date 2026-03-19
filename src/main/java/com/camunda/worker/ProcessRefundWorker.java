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
public class ProcessRefundWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.process-refund", autoComplete = false)
    public void processRefund(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String transactionId,
            @Variable double totalAmount,
            @Variable String customerEmail) {

        try {
            log.info("[ProcessRefund] orderId={} txn={} amount={}, variable: {}", orderId, transactionId, totalAmount, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

            // TODO: call payment gateway refund API
            log.info("[ProcessRefund] Refund issued refundId={} for orderId={}", refundId, orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "refundId", refundId,
                            "refundAmount", totalAmount,
                            "refundStatus", "PROCESSED",
                            "refundedAt", System.currentTimeMillis(),
                            "refundEmail", customerEmail))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[ProcessRefund] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
