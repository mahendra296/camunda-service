package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ValidateOrderWorker {

    @JobWorker(type = "order.validate")
    public Map<String, Object> validate(
            @Variable String orderId,
            @Variable String customerId,
            @Variable String customerEmail,
            @Variable List<Map<String, Object>> items) {

        log.info("[ValidateOrder] orderId={} customerId={}", orderId, customerId);

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
        return result;
    }
}
