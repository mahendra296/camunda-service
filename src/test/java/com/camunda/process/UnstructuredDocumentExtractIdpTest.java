package com.camunda.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class UnstructuredDocumentExtractIdpTest {

    private static final String PROCESS_ID = "unstructured-document-extract-idp";

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/unstructured-document-extract-idp.bpmn")
                .send()
                .join();
    }

    @Test
    void happyPath_extractsUnstructuredDocumentAndCompletes() {
        processTestContext.mockJobWorker("idp.store-document").thenComplete(Map.of("document", "doc-ref"));
        processTestContext
                .mockJobWorker("io.camunda:idp-extraction-connector-template:1")
                .thenComplete(Map.of("extractedDataResponse", Map.of("summary", "extracted text")));
        processTestContext.mockJobWorker("idp.print-extraction-result").thenComplete();

        var instance = client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of("documentPath", "/tmp/document.pdf"))
                .send()
                .join();

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder("task_store_document", "task_print_document", "end_extraction_complete");
    }
}
