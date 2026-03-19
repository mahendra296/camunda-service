package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SendDeliveryConfirmationWorker {

    @JobWorker(type = "order.send-delivery-confirmation")
    public Map<String, Object> sendDeliveryConfirmation(
            @Variable String orderId, @Variable String customerEmail, @Variable String trackingNumber) {

        log.info("[SendDeliveryConfirmation] orderId={} email={}", orderId, customerEmail);

        // TODO: send delivery confirmation email with review request
        log.info("[SendDeliveryConfirmation] Delivery confirmation sent to {} for order {}", customerEmail, orderId);

        return Map.of(
                "deliveryConfirmedAt",
                System.currentTimeMillis(),
                "orderStatus",
                "DELIVERED",
                "reviewRequestSent",
                true);
    }
}
