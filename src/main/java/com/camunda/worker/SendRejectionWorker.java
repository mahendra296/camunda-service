package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SendRejectionWorker {

    @JobWorker(type = "order.send-rejection")
    public Map<String, Object> sendRejection(
            @Variable String orderId, @Variable String customerEmail, @Variable String validationError) {

        log.warn("[SendRejection] orderId={} reason={}", orderId, validationError);

        // TODO: integrate with email/notification service
        log.info("[SendRejection] Rejection email sent to {} for order {}", customerEmail, orderId);

        return Map.of(
                "rejectionSentAt",
                System.currentTimeMillis(),
                "rejectionReason",
                validationError != null ? validationError : "Order validation failed");
    }
}
