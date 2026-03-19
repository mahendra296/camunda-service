package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendRejectionWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.send-rejection", autoComplete = false)
    public void sendRejection(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String validationError) {

        try {
            log.warn("[SendRejection] orderId={} reason={}, variable: {}", orderId, validationError, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: integrate with email/notification service
            log.info("[SendRejection] Rejection email sent to {} for order {}", customerEmail, orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "rejectionSentAt", System.currentTimeMillis(),
                            "rejectionReason", validationError != null ? validationError : "Order validation failed"))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[SendRejection] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
