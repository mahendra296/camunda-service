package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessCancellationWorker {

    @JobWorker(type = "order.process-cancellation")
    public Map<String, Object> processCancellation(
            @Variable String orderId,
            @Variable String customerEmail,
            @Variable String transactionId,
            @Variable String reservationId,
            @Variable(name = "cancellationReason", optional = true) String cancellationReason) {

        log.info("[ProcessCancellation] orderId={} reason={}", orderId, cancellationReason);

        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        // TODO: release inventory reservation
        // TODO: issue refund via payment gateway
        // TODO: notify customer
        log.info("[ProcessCancellation] Refund issued refundId={} for orderId={}", refundId, orderId);

        return Map.of(
                "cancellationReason",
                cancellationReason != null ? cancellationReason : "Customer requested",
                "refundId",
                refundId,
                "refundAmount",
                0.0, // TODO: fetch from process variable
                "cancelledAt",
                System.currentTimeMillis(),
                "inventoryReleased",
                true);
    }
}
