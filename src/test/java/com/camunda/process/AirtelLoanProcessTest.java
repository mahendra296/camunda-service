package com.camunda.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class AirtelLoanProcessTest {

    private static final String USSD_PROCESS_ID = "Process_Airtel";
    private static final String CAPBPM_PROCESS_ID = "airtel-loan-capbpm-process";
    private static final String MSISDN = "254700123456";

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/airtel-loan-process.bpmn")
                .send()
                .join();
    }

    private void publish(String messageName) {
        client.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(MSISDN)
                .send()
                .join();
    }

    private void publish(String messageName, Map<String, Object> variables) {
        client.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(MSISDN)
                .variables(variables)
                .send()
                .join();
    }

    @Test
    void ussdFlow_notEligible_endsWithoutLoanApplication() {
        processTestContext.mockJobWorker("airtel.selectLoanOption").thenComplete();
        processTestContext.mockJobWorker("airtel.sendLoanRequest").thenComplete();
        processTestContext.mockJobWorker("airtel.provideKycData").thenComplete();
        processTestContext.mockJobWorker("airtel.sendKycResponse").thenComplete();

        ProcessInstanceEvent instance = client.newCreateInstanceCommand()
                .bpmnProcessId(USSD_PROCESS_ID)
                .latestVersion()
                .variables(Map.of("msisdn", MSISDN))
                .send()
                .join();

        assertThat(instance).isWaitingForMessage("KycRequest");
        publish("KycRequest");

        assertThat(instance).isWaitingForMessage("OptedInNotification");
        publish("OptedInNotification");

        assertThat(instance).isWaitingForMessage("EligibilityResult");
        publish("EligibilityResult", Map.of("eligible", false));

        assertThat(instance).isCompleted().hasCompletedElements("airtel_end_not_eligible");
    }

    @Test
    void ussdFlow_eligible_completesThroughLoanApproval() {
        processTestContext.mockJobWorker("airtel.selectLoanOption").thenComplete();
        processTestContext.mockJobWorker("airtel.sendLoanRequest").thenComplete();
        processTestContext.mockJobWorker("airtel.provideKycData").thenComplete();
        processTestContext.mockJobWorker("airtel.sendKycResponse").thenComplete();
        processTestContext.mockJobWorker("airtel.sendLoanApplication").thenComplete();

        ProcessInstanceEvent instance = client.newCreateInstanceCommand()
                .bpmnProcessId(USSD_PROCESS_ID)
                .latestVersion()
                .variables(Map.of("msisdn", MSISDN))
                .send()
                .join();

        assertThat(instance).isWaitingForMessage("KycRequest");
        publish("KycRequest");
        assertThat(instance).isWaitingForMessage("OptedInNotification");
        publish("OptedInNotification");
        assertThat(instance).isWaitingForMessage("EligibilityResult");
        publish("EligibilityResult", Map.of("eligible", true));

        assertThat(instance).isWaitingForMessage("SubmissionConfirmation");
        publish("SubmissionConfirmation");
        assertThat(instance).isWaitingForMessage("LoanApproved");
        publish("LoanApproved");

        assertThat(instance).isCompleted().hasCompletedElements("airtel_end_approved");
    }

    @Test
    void capbpm_newCustomer_notEligible_endsAtNotEligible() {
        processTestContext.mockJobWorker("capbpm.checkCustomerExists").thenComplete(Map.of("customerExists", false));
        processTestContext.mockJobWorker("capbpm.fetchKycFromAirtel").thenComplete();
        processTestContext.mockJobWorker("capbpm.storeKycData").thenComplete();
        processTestContext.mockJobWorker("capbpm.optInCustomer").thenComplete();
        processTestContext.mockJobWorker("capbpm.notifyOptedIn").thenComplete();
        processTestContext.mockJobWorker("gnu.getCreditScore").thenComplete(Map.of("creditScore", 400));
        processTestContext.mockJobWorker("capbpm.sendEligibilityResult").thenComplete(Map.of("eligible", false));

        publish("LoanRequest", Map.of("msisdn", MSISDN));
        var instance = byProcessId(CAPBPM_PROCESS_ID);

        assertThat(instance).isWaitingForMessage("KycResponse");
        publish("KycResponse");

        assertThat(instance)
                .isCompleted()
                .hasCompletedElementsInOrder(
                        "task_fetch_kyc",
                        "task_store_kyc",
                        "task_optin_customer",
                        "task_notify_optedin",
                        "task_get_credit_score",
                        "task_send_eligibility",
                        "end_not_eligible");
    }

    @Test
    void capbpm_existingCustomerEligible_flowsThroughGnuAndCbsToDisbursement() {
        processTestContext.mockJobWorker("capbpm.checkCustomerExists").thenComplete(Map.of("customerExists", true));
        processTestContext.mockJobWorker("capbpm.notifyOptedIn").thenComplete();
        processTestContext.mockJobWorker("gnu.getCreditScore").thenComplete(Map.of("creditScore", 800));
        processTestContext.mockJobWorker("capbpm.sendEligibilityResult").thenComplete(Map.of("eligible", true));
        processTestContext.mockJobWorker("capbpm.storeLoanInfo").thenComplete();
        processTestContext.mockJobWorker("capbpm.sendSubmissionConfirmation").thenComplete();
        processTestContext.mockJobWorker("cbs.checkCustomer").thenComplete(Map.of("customerInCbs", false));
        processTestContext.mockJobWorker("cbs.onboardCustomer").thenComplete();
        processTestContext.mockJobWorker("cbs.processLoanDisbursement").thenComplete();
        processTestContext.mockJobWorker("capbpm.notifyLoanApproved").thenComplete();

        publish("LoanRequest", Map.of("msisdn", MSISDN));
        var instance = byProcessId(CAPBPM_PROCESS_ID);

        assertThat(instance).isWaitingForMessage("LoanApplication");
        publish("LoanApplication");

        assertThat(instance)
                .isCompleted()
                .hasNotActivatedElements("task_fetch_kyc", "task_store_kyc", "task_optin_customer")
                .hasCompletedElementsInOrder("task_check_cbs", "task_notify_approved", "capbpm_end");
    }
}
