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
class LoanApplicationFormProcessTest {

    private static final String PROCESS_ID = "loan-application-form-process";

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/loan-application-form-process.bpmn")
                .addResourceFromClasspath("workflow/loan-application-form.form")
                .send()
                .join();
    }

    private ProcessInstanceEvent startProcess() {
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .send()
                .join();
    }

    @Test
    void validFormData_isSubmittedForReview() {
        var instance = startProcess();

        processTestContext.completeUserTask("task_fill_application", Map.of("applicantName", "Jane Doe"));
        processTestContext.mockJobWorker("form.validate-application-data").thenComplete(Map.of("formValid", true));
        processTestContext.mockJobWorker("form.submit-for-review").thenComplete();

        assertThat(instance).isCompleted().hasCompletedElementsInOrder("task_fill_application", "end_submitted");
    }

    @Test
    void invalidFormData_notifiesIncomplete() {
        var instance = startProcess();

        processTestContext.completeUserTask("task_fill_application", Map.of());
        processTestContext.mockJobWorker("form.validate-application-data").thenComplete(Map.of("formValid", false));
        processTestContext.mockJobWorker("form.notify-incomplete").thenComplete();

        assertThat(instance).isCompleted().hasCompletedElementsInOrder("task_fill_application", "end_form_invalid");
    }
}
