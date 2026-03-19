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
public class HandleSlaBreachWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.handle-sla-breach", autoComplete = false)
    public void handleSlaBreach(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String trackingNumber,
            @Variable String carrierName) {

        try {
            log.error("[HandleSlaBreach] SHIPPING SLA BREACHED — orderId={} tracking={} carrier={}, variable: {}", orderId, trackingNumber, carrierName, objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            // TODO: alert operations team
            // TODO: contact carrier for expedited delivery
            // TODO: proactively notify customer

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "slaBreachHandledAt", System.currentTimeMillis(),
                            "opsAlerted", true,
                            "customerNotified", true,
                            "carrierEscalated", true))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[HandleSlaBreach] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
