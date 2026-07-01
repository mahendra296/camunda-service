package com.camunda.worker.loan;

import com.camunda.dto.ValidateApplicationResponse;
import com.camunda.model.RiskConfig;
import com.camunda.model.RiskConfigRepository;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Validates a loan application and loads dynamic risk thresholds from the {@code risk_config}
 * table, publishing them as a single {@code riskConfig} context object so the downstream DMN can
 * reference thresholds by name (e.g. {@code riskConfig.csExcellentMin}).
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code applicationValid} — true when all fields pass validation
 *   <li>{@code validationError} — human-readable reason when invalid, null otherwise
 *   <li>{@code validatedAt} — epoch millis of validation
 *   <li>{@code riskConfig} — context object containing all threshold keys from {@code risk_config}
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ValidateLoanApplicationWorker {

    private static final Set<String> INTEGER_KEYS = Set.of(
            "csExcellentMin",
            "csExcellentMax",
            "csVeryGoodMin",
            "csVeryGoodMax",
            "csGoodMin",
            "csAcceptableMin",
            "csFairMin",
            "csFairMax",
            "csPoorMax",
            "ageStandard",
            "ageMature",
            "ageYoungMin",
            "ageYoungMax");

    private final RiskConfigRepository riskConfigRepository;

    @JobWorker(type = "loan.validate-application", autoComplete = false)
    public void handle(
            JobClient client,
            ActivatedJob job,
            @Variable String applicationId,
            @Variable String applicantName,
            @Variable int creditScore,
            @Variable int applicantAge,
            @Variable double monthlyIncome,
            @Variable double requestedAmount) {

        log.info(
                "[Loan][ValidateApplication] type={} key={} applicationId={}",
                job.getType(),
                job.getKey(),
                applicationId);

        try {
            var validationError = validate(applicantName, applicantAge, monthlyIncome, requestedAmount);
            boolean valid = validationError == null;

            log.info(
                    "[Loan][ValidateApplication] applicationId={} valid={} error={}",
                    applicationId,
                    valid,
                    validationError);

            Map<String, Object> riskConfig = riskConfigRepository.findAll().stream()
                    .collect(Collectors.toMap(
                            RiskConfig::getConfigKey,
                            config -> INTEGER_KEYS.contains(config.getConfigKey())
                                    ? (Object) config.getConfigValue().intValue()
                                    : config.getConfigValue().doubleValue()));

            log.info("[Loan][ValidateApplication] Loaded {} risk threshold entries into riskConfig", riskConfig.size());

            var response = ValidateApplicationResponse.builder()
                    .applicationValid(valid)
                    .validationError(validationError)
                    .validatedAt(System.currentTimeMillis())
                    .creditScore(creditScore)
                    .applicantAge(applicantAge)
                    .monthlyIncome(monthlyIncome)
                    .requestedAmount(requestedAmount)
                    .riskConfig(riskConfig)
                    .build();

            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("validateApplicationResponse", response))
                    .send()
                    .join();

        } catch (Exception e) {
            log.error("[Loan][ValidateApplication] Failed applicationId={}", applicationId, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    // ──────────────────────────────────────────────
    private String validate(String applicantName, int applicantAge, double monthlyIncome, double requestedAmount) {
        if (applicantName == null || applicantName.isBlank()) {
            return "Applicant name is required";
        }
        if (applicantAge < 18) {
            return "Applicant must be at least 18 years old";
        }
        if (monthlyIncome <= 0) {
            return "Monthly income must be greater than zero";
        }
        if (requestedAmount <= 0) {
            return "Requested amount must be greater than zero";
        }
        if (requestedAmount > monthlyIncome * 60) {
            return "Requested amount exceeds maximum allowable loan (60× monthly income)";
        }
        return null;
    }
}
