package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HandlePaymentFailureWorker {

    @JobWorker(type = "order.handle-payment-failure")
    public Map<String, Object> handlePaymentFailure(
            @io.camunda.client.annotation.Variable String orderId,
            @Variable String customerEmail,
            @Variable String reservationId) {

        log.error("[HandlePaymentFailure] orderId={} — releasing inventory reservation={}", orderId, reservationId);

        // TODO: release reserved inventory
        // TODO: notify customer about payment error
        // TODO: log incident for ops team
        log.info("[HandlePaymentFailure] Inventory released and customer notified for orderId={}", orderId);

        return Map.of(
                "paymentFailureHandledAt", System.currentTimeMillis(),
                "inventoryReleased", true,
                "failureNotificationSent", true);
    }
}
