package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessRefundWorker {

    @JobWorker(type = "order.process-refund")
    public Map<String, Object> processRefund(
            @Variable String orderId,
            @Variable String transactionId,
            @Variable double totalAmount,
            @Variable String customerEmail) {

        log.info("[ProcessRefund] orderId={} txn={} amount={}", orderId, transactionId, totalAmount);

        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        // TODO: call payment gateway refund API
        log.info("[ProcessRefund] Refund issued refundId={} for orderId={}", refundId, orderId);

        return Map.of(
                "refundId", refundId,
                "refundAmount", totalAmount,
                "refundStatus", "PROCESSED",
                "refundedAt", System.currentTimeMillis(),
                "refundEmail", customerEmail);
    }
}
