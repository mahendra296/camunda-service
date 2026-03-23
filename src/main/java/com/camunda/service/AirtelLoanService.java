package com.camunda.service;

import com.camunda.dto.AirtelLoanRequest;
import com.camunda.dto.AirtelLoanResponse;
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
public class AirtelLoanService {

    private final CamundaClient camundaClient;

    /**
     * Starts the Airtel process (Process_Airtel) which simulates the customer USSD journey.
     * The Airtel process communicates with the CAPBPM process via Zeebe messages published by
     * dedicated job workers.
     */
    public AirtelLoanResponse startLoanProcess(AirtelLoanRequest request) {
        log.info("[AirtelLoanService] Starting Airtel process for msisdn={}", request.getMsisdn());

        var variables = buildInitialVariables(request);

        ProcessInstanceEvent event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("Process_Airtel")
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        log.info(
                "[AirtelLoanService] Airtel process started. instanceKey={} msisdn={}",
                event.getProcessInstanceKey(),
                request.getMsisdn());

        return new AirtelLoanResponse(
                request.getMsisdn(),
                event.getProcessInstanceKey(),
                "STARTED",
                "Airtel loan process started — workers will orchestrate messaging with CAPBPM");
    }

    // ──────────────────────────────────────────────
    private Map<String, Object> buildInitialVariables(AirtelLoanRequest request) {
        var vars = new HashMap<String, Object>();
        vars.put("msisdn", request.getMsisdn());
        vars.put("loanAmount", request.getLoanAmount());
        vars.put("tenureMonths", request.getTenureMonths());
        vars.put("loanPurpose", request.getLoanPurpose() != null ? request.getLoanPurpose() : "PERSONAL");
        if (request.getCustomerExists() != null) {
            vars.put("customerExists", request.getCustomerExists());
        }
        vars.put("startedAt", System.currentTimeMillis());
        return vars;
    }
}
