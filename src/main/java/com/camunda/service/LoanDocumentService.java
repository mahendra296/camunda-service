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
                "[LoanDocumentService] Starting IDP processing documentId={} loanNumber={} document={}",
                request.getDocumentId(),
                request.getLoanNumber(),
                request.getDocumentPath());

        var variables = buildVariables(request);
        var bpmnProcessId = resolveBpmnProcessId(request.getExportType());

        ProcessInstanceEvent event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .variables(variables)
                .send()
                .join();

        log.info(
                "[LoanDocumentService] Process started. bpmnProcessId={} instanceKey={} documentId={}",
                bpmnProcessId,
                event.getProcessInstanceKey(),
                request.getDocumentId());

        return new LoanDocumentProcessResponse(
                request.getDocumentId(),
                event.getProcessInstanceKey(),
                "STARTED",
                "Document uploaded — workers will store, extract, and validate the loan document");
    }

    // ──────────────────────────────────────────────
    // loan-document-idp-process.bpmn was split into 3 standalone processes, one per IDP
    // template; the exportType-based routing that used to be an in-process exclusive gateway
    // now happens here instead, picking which process definition to start.
    private String resolveBpmnProcessId(int exportType) {
        return switch (exportType) {
            case 0 -> "structured-document-extract-idp";
            case 1 -> "unstructured-document-extract-idp";
            default -> "unstructured-with-image-document-extract-idp";
        };
    }

    private Map<String, Object> buildVariables(LoanDocumentUploadRequest request) {
        var vars = new HashMap<String, Object>();
        vars.put("documentId", request.getDocumentId());
        vars.put("loanNumber", request.getLoanNumber());
        vars.put("documentPath", request.getDocumentPath());
        vars.put("uploadedAt", System.currentTimeMillis());
        vars.put("exportType", request.getExportType());
        return vars;
    }
}
