package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SendConfirmationWorker {

    @JobWorker(type = "order.send-confirmation")
    public Map<String, Object> sendConfirmation(
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String customerName,
            @Variable double totalAmount,
            @Variable String transactionId) {

        log.info("[SendConfirmation] orderId={} email={}", orderId, customerEmail);

        // TODO: use email/notification service
        log.info(
                "[SendConfirmation] Order confirmation sent to {} | txn={} amount={}",
                customerEmail,
                transactionId,
                totalAmount);

        return Map.of("confirmationSentAt", System.currentTimeMillis(), "confirmationEmail", customerEmail);
    }
}
