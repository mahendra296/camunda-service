package com.camunda.service;

import com.camunda.dto.PaymentRequest;
import com.camunda.dto.PaymentResponse;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundService {

    private final CamundaClient camundaClient;

    /**
     * Starts the payment-refund-process BPMN.
     *
     * <p>Simulation guide (controlled via request fields):
     * <ul>
     *   <li>{@code amount > 10000} → ChargePaymentWorker throws PAYMENT_FAILED (INSUFFICIENT_FUNDS)
     *       → error boundary fires → compensation triggered → failure notification sent</li>
     *   <li>{@code paymentMethod = "INVALID"} → ChargePaymentWorker throws PAYMENT_FAILED
     *       (INVALID_PAYMENT_METHOD) → same error path</li>
     *   <li>Any other valid input → happy path: reserve → charge → confirm → success</li>
     * </ul>
     */
    public PaymentResponse startPaymentProcess(PaymentRequest request) {
        log.info(
                "[PaymentRefundService] Starting process paymentId={} customerId={} amount={}",
                request.getPaymentId(),
                request.getCustomerId(),
                request.getAmount());

        var variables = buildInitialVariables(request);

        ProcessInstanceEvent event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("payment-refund-process")
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        log.info(
                "[PaymentRefundService] Process started instanceKey={} paymentId={}",
                event.getProcessInstanceKey(),
                request.getPaymentId());

        return new PaymentResponse(
                request.getPaymentId(),
                event.getProcessInstanceKey(),
                "STARTED",
                "Payment process started — workers will orchestrate the flow");
    }

    // ──────────────────────────────────────────────────────────────────────
    private Map<String, Object> buildInitialVariables(PaymentRequest request) {
        var vars = new HashMap<String, Object>();
        vars.put("paymentId", request.getPaymentId());
        vars.put("customerId", request.getCustomerId());
        vars.put("amount", request.getAmount());
        vars.put("currency", request.getCurrency());
        vars.put("paymentMethod", request.getPaymentMethod());
        vars.put("startedAt", System.currentTimeMillis());
        return vars;
    }
}
