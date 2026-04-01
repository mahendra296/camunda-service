package com.camunda.controller;

import com.camunda.dto.PaymentRequest;
import com.camunda.dto.PaymentResponse;
import com.camunda.service.PaymentRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentRefundController {

    private final PaymentRefundService paymentRefundService;

    /**
     * Starts the payment-refund-process BPMN.
     *
     * <p>Simulation scenarios:
     * <ul>
     *   <li>Normal payment → any valid request with amount ≤ 10,000</li>
     *   <li>Trigger PAYMENT_FAILED error boundary → set {@code amount > 10000}
     *       or {@code paymentMethod = "INVALID"}</li>
     * </ul>
     *
     * <p>POST /api/payments/start
     */
    @PostMapping("/start")
    public ResponseEntity<PaymentResponse> startPayment(
            @Valid @RequestBody PaymentRequest request) {
        log.info("[API] POST /api/payments/start paymentId={}", request.getPaymentId());
        var response = paymentRefundService.startPaymentProcess(request);
        return ResponseEntity.ok(response);
    }
}
