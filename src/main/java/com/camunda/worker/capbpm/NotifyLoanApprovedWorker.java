package com.camunda.worker.capbpm;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CAPBPM — publishes a LoanApproved message to the Airtel process, informing it that the loan has
 * been approved and disbursed via CBS.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanApprovalNotified} — true when Airtel has been informed
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyLoanApprovedWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "capbpm.notifyLoanApproved", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String msisdn,
            @Variable(optional = true) String loanId,
            @Variable(optional = true) String cbsAccountNo,
            @Variable(optional = true) String disbursementRef) {

        log.info(
                "[CAPBPM][NotifyLoanApproved] type={} key={} msisdn={} loanId={} account={} disbRef={}",
                job.getType(),
                job.getKey(),
                msisdn,
                loanId,
                cbsAccountNo,
                disbursementRef);

        try {
            camundaClient
                    .newPublishMessageCommand()
                    .messageName("LoanApproved")
                    .correlationKey(msisdn)
                    .timeToLive(Duration.ofMinutes(30))
                    .variables(Map.of(
                            "loanId", loanId != null ? loanId : "",
                            "cbsAccountNo", cbsAccountNo != null ? cbsAccountNo : "",
                            "disbursementRef", disbursementRef != null ? disbursementRef : "",
                            "loanApprovedAt", System.currentTimeMillis()))
                    .send()
                    .join();

            log.info(
                    "[CAPBPM][NotifyLoanApproved] LoanApproved published to Airtel: msisdn={} loanId={} DISBURSED",
                    msisdn,
                    loanId);

            client.newCompleteCommand(job.getKey())
                    .variables(
                            Map.of("loanApprovalNotified", true, "loanApprovalNotifiedAt", System.currentTimeMillis()))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[CAPBPM][NotifyLoanApproved] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }
}
