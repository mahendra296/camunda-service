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
public class SendDeliveryConfirmationWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.send-delivery-confirmation", autoComplete = false)
    public void sendDeliveryConfirmation(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String trackingNumber) {

        try {
            log.info("[SendDeliveryConfirmation] orderId={} email={}, variable: {}", orderId, customerEmail, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: send delivery confirmation email with review request
            log.info("[SendDeliveryConfirmation] Delivery confirmation sent to {} for order {}", customerEmail, orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "deliveryConfirmedAt", System.currentTimeMillis(),
                            "orderStatus", "DELIVERED",
                            "reviewRequestSent", true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[SendDeliveryConfirmation] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
