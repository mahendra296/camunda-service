package com.camunda.worker.idp;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Uploads the loan document at its local filesystem path (e.g. {@code D:\Loan.pdf}) into Camunda
 * 8's native Document Store via {@code CamundaClient.newCreateDocumentCommand()}. Camunda's own
 * document store acts as the object storage — no custom persistence layer is needed, and the
 * resulting reference is viewable/downloadable directly from Operate and Tasklist.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanDocument} — Camunda document reference ({@link DocumentReferenceResponse})
 *   <li>{@code documentStoredAt} — epoch millis of storage
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class StoreLoanDocumentWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "idp.store-document", autoComplete = false)
    public void handle(JobClient client, ActivatedJob job, @Variable String documentId, @Variable String documentPath) {

        log.info(
                "[IDP][StoreDocument] type={} key={} documentId={} documentPath={}",
                job.getType(),
                job.getKey(),
                documentId,
                documentPath);

        try {
            var sourcePath = Paths.get(documentPath);
            if (!Files.isRegularFile(sourcePath)) {
                throw new IOException("Document not found at path: " + documentPath);
            }

            var contentType = Files.probeContentType(sourcePath);

            DocumentReferenceResponse loanDocument;
            try (var content = Files.newInputStream(sourcePath)) {
                loanDocument = camundaClient
                        .newCreateDocumentCommand()
                        .content(content)
                        .fileName(sourcePath.getFileName().toString())
                        .contentType(contentType != null ? contentType : "application/octet-stream")
                        .send()
                        .join();
            }

            log.info(
                    "[IDP][StoreDocument] Uploaded documentId={} to Camunda document store"
                            + " (camundaDocumentId={} storeId={})",
                    documentId,
                    loanDocument.getDocumentId(),
                    loanDocument.getStoreId());

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("loanDocument", loanDocument, "documentStoredAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[IDP][StoreDocument] Failed documentId={} documentPath={}", documentId, documentPath, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
