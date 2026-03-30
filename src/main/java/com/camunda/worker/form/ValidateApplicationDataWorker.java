package com.camunda.worker.form;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validates data submitted via the Loan Application Form.
 *
 * <p>Reads every field collected by {@code loan-application-form.form} and applies
 * business validation rules before the process proceeds.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code formValid} — {@code true} if all rules pass
 *   <li>{@code formValidationError} — human-readable reason when invalid (null otherwise)
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidateApplicationDataWorker {

    @JobWorker(type = "form.validate-application-data", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicantName,
            @Variable String nationalId,
            @Variable String emailAddress,
            @Variable String phoneNumber,
            @Variable(name = "employmentType") String employmentType,
            @Variable double monthlyIncome,
            @Variable double requestedAmount,
            @Variable int tenureMonths,
            @Variable(name = "loanPurpose") String loanPurpose,
            @Variable(name = "termsAccepted") Boolean termsAccepted) {

        log.info(
                "[Form][ValidateApplicationData] type={} key={} applicant={}",
                job.getType(),
                job.getKey(),
                applicantName);

        try {
            var validationError = validate(
                    applicantName,
                    nationalId,
                    emailAddress,
                    phoneNumber,
                    employmentType,
                    monthlyIncome,
                    requestedAmount,
                    tenureMonths,
                    loanPurpose,
                    termsAccepted);

            boolean valid = validationError == null;

            log.info(
                    "[Form][ValidateApplicationData] applicant={} valid={} error={}",
                    applicantName,
                    valid,
                    validationError);

            Map<String, Object> vars = new HashMap<>();
            vars.put("formValid", valid);
            vars.put("formValidationError", validationError);
            vars.put("formValidatedAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[Form][ValidateApplicationData] Failed applicant={}", applicantName, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    // ──────────────────────────────────────────────
    private String validate(
            String applicantName,
            String nationalId,
            String emailAddress,
            String phoneNumber,
            String employmentType,
            double monthlyIncome,
            double requestedAmount,
            int tenureMonths,
            String loanPurpose,
            Boolean termsAccepted) {

        if (isBlank(applicantName)) return "Applicant name is required";
        if (isBlank(nationalId)) return "National ID is required";
        if (isBlank(emailAddress) || !emailAddress.contains("@")) return "Valid email address is required";
        if (isBlank(phoneNumber)) return "Phone number is required";
        if (isBlank(employmentType)) return "Employment type is required";
        if (monthlyIncome <= 0) return "Monthly income must be greater than zero";
        if (requestedAmount <= 0) return "Requested loan amount must be greater than zero";
        if (tenureMonths < 1 || tenureMonths > 60) return "Loan tenure must be between 1 and 60 months";
        if (isBlank(loanPurpose)) return "Loan purpose is required";
        if (!Boolean.TRUE.equals(termsAccepted)) return "Terms and conditions must be accepted";
        if (requestedAmount > monthlyIncome * 60)
            return "Requested amount exceeds maximum allowable loan (60× monthly income)";
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
