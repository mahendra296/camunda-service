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
public class ProcessCancellationWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.process-cancellation", autoComplete = false)
    public void processCancellation(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String transactionId,
            @Variable String reservationId,
            @Variable(name = "cancellationReason", optional = true) String cancellationReason) {

        try {
            log.info("[ProcessCancellation] orderId={} reason={}, variable: {}", orderId, cancellationReason, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

            // TODO: release inventory reservation
            // TODO: issue refund via payment gateway
            // TODO: notify customer
            log.info("[ProcessCancellation] Refund issued refundId={} for orderId={}", refundId, orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "cancellationReason", cancellationReason != null ? cancellationReason : "Customer requested",
                            "refundId", refundId,
                            "refundAmount", 0.0, // TODO: fetch from process variable
                            "cancelledAt", System.currentTimeMillis(),
                            "inventoryReleased", true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[ProcessCancellation] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
