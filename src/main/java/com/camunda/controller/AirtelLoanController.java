package com.camunda.controller;

import com.camunda.dto.AirtelLoanRequest;
import com.camunda.dto.AirtelLoanResponse;
import com.camunda.service.AirtelLoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/airtel")
@RequiredArgsConstructor
public class AirtelLoanController {

    private final AirtelLoanService airtelLoanService;

    /**
     * Starts the Airtel process (simulates customer dialing *123#).
     * The Airtel process automatically sends a LoanRequest message to start CapBPM,
     * after which both pools run and exchange messages end-to-end.
     *
     * POST /api/airtel/loans/apply
     */
    @PostMapping("/loans/apply")
    public ResponseEntity<AirtelLoanResponse> applyLoan(@Valid @RequestBody AirtelLoanRequest request) {
        log.info("[API] POST /api/airtel/loans/apply msisdn={}", request.getMsisdn());
        var response = airtelLoanService.startLoanProcess(request);
        return ResponseEntity.ok(response);
    }
}
