package com.camunda.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class UnstructuredDocumentExtractScreenshotIdpTest {

    private static final String PROCESS_ID = "UnstructuredDocumentExtractScreenshotIDPProcess";

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/unstructured-document-extract-screenshot-idp.bpmn")
                .send()
                .join();
    }

    private ProcessInstanceEvent start(boolean signatureUploaded) {
        processTestContext.mockJobWorker("idp.store-document").thenComplete(Map.of("document", "doc-ref"));
        processTestContext
                .mockJobWorker("io.camunda:idp-extraction-connector-template:1")
                .thenComplete(Map.of("signInfo", Map.of("isSignatureUploaded", signatureUploaded)));

        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of("documentPath", "/tmp/document.pdf"))
                .send()
                .join();
    }

    @Test
    void signatureUploaded_cropsSignatureRegion() {
        processTestContext.mockJobWorker("idp.extract-pdf-region").thenComplete();

        var instance = start(true);

        assertThat(instance)
                .isCompleted()
                .hasCompletedElements("CropSignatureRegionFromPDFTask", "PDFRegionExtractedEndEvent");
    }

    @Test
    void noSignatureUploaded_skipsCropping() {
        var instance = start(false);

        assertThat(instance)
                .isCompleted()
                .hasCompletedElements("NoImageUploadedEndEvent")
                .hasNotActivatedElements("CropSignatureRegionFromPDFTask");
    }
}
