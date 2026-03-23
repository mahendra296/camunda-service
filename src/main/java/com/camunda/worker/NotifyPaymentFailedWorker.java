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
public class NotifyPaymentFailedWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.notify-payment-failed", autoComplete = false)
    public void notifyPaymentFailed(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String paymentDeclineCode) {

        try {
            log.warn(
                    "[NotifyPaymentFailed] orderId={} declineCode={}, variable: {}",
                    orderId,
                    paymentDeclineCode,
                    objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: send decline notification to customer
            log.info("[NotifyPaymentFailed] Decline email sent to {} for orderId={}", customerEmail, orderId);

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("declineNotificationSent", true, "declineNotifiedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[NotifyPaymentFailed] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
