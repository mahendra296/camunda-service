package com.camunda.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HandleSlaBreachWorker {

    @JobWorker(type = "order.handle-sla-breach")
    public Map<String, Object> handleSlaBreach(
            @Variable String orderId, @Variable String trackingNumber, @Variable String carrierName) {

        log.error(
                "[HandleSlaBreach] SHIPPING SLA BREACHED — orderId={} tracking={} carrier={}",
                orderId,
                trackingNumber,
                carrierName);

        // TODO: alert operations team
        // TODO: contact carrier for expedited delivery
        // TODO: proactively notify customer

        return Map.of(
                "slaBreachHandledAt", System.currentTimeMillis(),
                "opsAlerted", true,
                "customerNotified", true,
                "carrierEscalated", true);
    }
}
