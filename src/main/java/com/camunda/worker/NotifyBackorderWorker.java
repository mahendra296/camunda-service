package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyBackorderWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.notify-backorder", autoComplete = false)
    public void notifyBackorder(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable List<String> outOfStockItems) {

        try {
            log.info(
                    "[NotifyBackorder] orderId={} outOfStock={}, variable: {}",
                    orderId,
                    outOfStockItems,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: send backorder notification email
            log.info(
                    "[NotifyBackorder] Backorder notification sent to {} for items {}", customerEmail, outOfStockItems);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("backorderNotifiedAt", System.currentTimeMillis(), "backorderAttempt", 1))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[NotifyBackorder] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
