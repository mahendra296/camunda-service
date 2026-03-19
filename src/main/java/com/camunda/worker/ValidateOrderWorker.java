package com.camunda.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateOrderWorker {

    private final ObjectMapper objectMapper;

    @JobWorker(type = "order.validate", autoComplete = false)
    public void validate(
            JobClient client,
            ActivatedJob job,
            @Variable String orderId,
            @Variable String customerId,
            @Variable String customerEmail,
            @Variable List<Map<String, Object>> items) {

        try {
            log.info("[ValidateOrder] orderId={} customerId={}, Retries : {}, variable: {}", orderId, customerId, job.getRetries(), objectMapper.writeValueAsString(job.getVariablesAsMap()));
        } catch (JsonProcessingException e) {
            log.error("Error while print the message");
        }

        try {
            boolean orderValid = true;
            String validationError = null;
            if (items == null || items.isEmpty()) {
                orderValid = false;
                validationError = "Order must contain at least one item";
            } else if (customerEmail == null || !customerEmail.contains("@")) {
                orderValid = false;
                validationError = "Invalid customer email";
            }

            // Calculate total amount
            double totalAmount = 0;
            if (items != null) {
                for (Map<String, Object> item : items) {
                    Number qty = (Number) item.get("quantity");
                    Number price = (Number) item.get("unitPrice");
                    if (qty != null && price != null) {
                        totalAmount += qty.doubleValue() * price.doubleValue();
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("orderValid", orderValid);
            result.put("totalAmount", totalAmount);
            result.put("validationError", validationError);
            result.put("validatedAt", System.currentTimeMillis());

            log.info("[ValidateOrder] orderId={} valid={} totalAmount={}", orderId, orderValid, totalAmount);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[ValidateOrder] Failed for orderId={}", orderId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
