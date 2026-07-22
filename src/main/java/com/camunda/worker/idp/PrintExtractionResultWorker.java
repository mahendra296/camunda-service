package com.camunda.worker.idp;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs the IDP extraction result for whichever template ran: structured-document-extract-idp,
 * unstructured-document-extract-idp, or unstructured-with-image-document-extract-idp.bpmn. Reused
 * across all three processes' "Print Result" task — each task's zeebe:input mapping supplies its
 * own templateLabel and result variables, so a single job type covers all three.
 */
@Slf4j
@Component
public class PrintExtractionResultWorker {

    @JobWorker(type = "idp.print-extraction-result", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String templateLabel,
            @Variable Map<String, Object> extractionResult,
            @Variable Map<String, Object> extractedData) {

        log.info(
                "[IDP][PrintResult] type={} key={} template={} idpExtractionResult={} extractedDataResponse={}",
                job.getType(),
                job.getKey(),
                templateLabel,
                extractionResult,
                extractedData);

        try {
            client.newCompleteCommand(job.getKey()).send().join();
        } catch (Exception e) {
            log.error("[IDP][PrintResult] Failed template={}", templateLabel, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
