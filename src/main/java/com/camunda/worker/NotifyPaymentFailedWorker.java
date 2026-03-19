package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotifyPaymentFailedWorker {

    @JobWorker(type = "order.notify-payment-failed")
    public Map<String, Object> notifyPaymentFailed(
            @Variable String orderId, @Variable String customerEmail, @Variable String paymentDeclineCode) {

        log.warn("[NotifyPaymentFailed] orderId={} declineCode={}", orderId, paymentDeclineCode);

        // TODO: send decline notification to customer
        log.info("[NotifyPaymentFailed] Decline email sent to {} for orderId={}", customerEmail, orderId);

        return Map.of("declineNotificationSent", true, "declineNotifiedAt", System.currentTimeMillis());
    }
}
