package com.camunda.worker.idp;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Registers the loan using the (possibly human-corrected) extracted document data. Simulates the
 * downstream {@code POST /loan/register} business API call.
 *
 * <p>{@code extractedDataResponse.loanAmount} may arrive either as a {@code Number} (human-reviewed
 * path, see {@code UserTaskService#completeLoanDocumentReview}) or as a raw OCR {@code String} (IDP
 * connector path, e.g. {@code "$350,000.00"}), so it is parsed defensively.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code loanId} — generated loan registration reference
 *   <li>{@code loanRegistered} — {@code true}
 *   <li>{@code registeredAt} — epoch millis of registration
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterLoanFromDocumentWorker {

    @JobWorker(type = "idp.register-loan", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String documentId,
            @Variable Map<String, Object> extractedDataResponse) {

        var borrowerName = (String) extractedDataResponse.get("borrowerName");
        var loanAmount = parseLoanAmount(extractedDataResponse.get("loanAmount"));

        log.info(
                "[IDP][RegisterLoan] type={} key={} documentId={} borrower={} amount={}",
                job.getType(),
                job.getKey(),
                documentId,
                borrowerName,
                loanAmount);

        try {
            var loanId = "LN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            log.info(
                    "[IDP][RegisterLoan] Registered loanId={} borrower={} documentId={}",
                    loanId,
                    borrowerName,
                    documentId);

            Map<String, Object> vars = new HashMap<>();
            vars.put("loanId", loanId);
            vars.put("loanRegistered", true);
            vars.put("registeredAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[IDP][RegisterLoan] Failed documentId={}", documentId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    // ──────────────────────────────────────────────
    private double parseLoanAmount(Object rawLoanAmount) {
        if (rawLoanAmount instanceof Number number) {
            return number.doubleValue();
        }
        var digitsOnly = String.valueOf(rawLoanAmount).replaceAll("[^0-9.]", "");
        return digitsOnly.isBlank() ? 0.0 : Double.parseDouble(digitsOnly);
    }
}
