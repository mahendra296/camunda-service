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
public class SendConfirmationWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.send-confirmation", autoComplete = false)
    public void sendConfirmation(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String customerName,
            @Variable double totalAmount,
            @Variable String transactionId) {

        try {
            log.info(
                    "[SendConfirmation] orderId={} email={}, variable: {}",
                    orderId,
                    customerEmail,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: use email/notification service
            log.info(
                    "[SendConfirmation] Order confirmation sent to {} | txn={} amount={}",
                    customerEmail,
                    transactionId,
                    totalAmount);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "confirmationSentAt", System.currentTimeMillis(), "confirmationEmail", customerEmail))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[SendConfirmation] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
