package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotifyBackorderWorker {

    @JobWorker(type = "order.notify-backorder")
    public Map<String, Object> notifyBackorder(
            @Variable String orderId, @Variable String customerEmail, @Variable List<String> outOfStockItems) {

        log.info("[NotifyBackorder] orderId={} outOfStock={}", orderId, outOfStockItems);

        // TODO: send backorder notification email
        log.info("[NotifyBackorder] Backorder notification sent to {} for items {}", customerEmail, outOfStockItems);

        return Map.of("backorderNotifiedAt", System.currentTimeMillis(), "backorderAttempt", 1);
    }
}
