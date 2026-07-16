package com.camunda.service;

import com.camunda.dto.LoanDocumentProcessResponse;
import com.camunda.dto.LoanDocumentUploadRequest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class LoanDocumentService {

    private final CamundaClient camundaClient;

    public LoanDocumentProcessResponse startProcessing(LoanDocumentUploadRequest request) {
        log.info(
                "[LoanDocumentService] Starting IDP processing documentId={} loanNumber={} documentPath={}",
                request.getDocumentId(),
                request.getLoanNumber(),
                request.getDocumentPath());

        var variables = buildVariables(request);

        ProcessInstanceEvent event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("loan-document-idp-process")
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        log.info(
                "[LoanDocumentService] Process started. instanceKey={} documentId={}",
                event.getProcessInstanceKey(),
                request.getDocumentId());

        return new LoanDocumentProcessResponse(
                request.getDocumentId(),
                event.getProcessInstanceKey(),
                "STARTED",
                "Document uploaded — workers will store, extract, and validate the loan document");
    }

    // ──────────────────────────────────────────────
    private Map<String, Object> buildVariables(LoanDocumentUploadRequest request) {
        var vars = new HashMap<String, Object>();
        vars.put("documentId", request.getDocumentId());
        vars.put("loanNumber", request.getLoanNumber());
        vars.put("documentPath", request.getDocumentPath());
        if (request.getSimulatedConfidence() != null) {
            vars.put("simulatedConfidence", request.getSimulatedConfidence());
        }
        vars.put("uploadedAt", System.currentTimeMillis());
        return vars;
    }
}
