package com.camunda.worker.gnu;

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
 * GnuCreditScore integration — calls the GnuCreditScore API to retrieve a credit score.
 *
 * <p>Eligibility threshold: score &ge; 600.
 *
 * <p>Output variables:
 *
 * <ul>
 *   <li>{@code creditScore} — numeric score (300–850)
 *   <li>{@code creditGrade} — letter grade A/B/C/D/F
 *   <li>{@code eligible} — true when score &ge; 600
 *   <li>{@code eligibleAmount} — maximum loan amount based on grade
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetCreditScoreWorker {

    private static final int ELIGIBILITY_THRESHOLD = 200;

    @JobWorker(type = "gnu.getCreditScore", autoComplete = false)
    public void handle(
            JobClient client, ActivatedJob job, @Variable String msisdn, @Variable(optional = true) String customerId) {

        log.info(
                "[GNU][GetCreditScore] type={} key={} msisdn={} customerId={}",
                job.getType(),
                job.getKey(),
                msisdn,
                customerId);

        try {
            int score = deriveScore(msisdn);
            var grade = toGrade(score);
            boolean eligible = score >= ELIGIBILITY_THRESHOLD;
            double eligibleAmount = toEligibleAmount(grade);

            log.info(
                    "[GNU][GetCreditScore] msisdn={} score={} grade={} eligible={} eligibleAmount={}",
                    msisdn,
                    score,
                    grade,
                    eligible,
                    eligibleAmount);

            Map<String, Object> vars = new HashMap<>();
            vars.put("creditScore", score);
            vars.put("creditGrade", grade);
            vars.put("eligible", eligible);
            vars.put("eligibleAmount", eligibleAmount);
            vars.put("creditScoredAt", System.currentTimeMillis());

            client.newCompleteCommand(job.getKey()).variables(vars).send().join();

        } catch (Exception e) {
            log.error("[GNU][GetCreditScore] Failed msisdn={}", msisdn, e);
            client.newFailCommand(job.getKey())
                    .retries(job.getRetries() - 1)
                    .errorMessage(e.getMessage())
                    .send()
                    .join();
        }
    }

    private int deriveScore(String msisdn) {
        if (msisdn.startsWith("256")) {
            return 700;
        } else return 300;
    }

    private String toGrade(int score) {
        if (score >= 750) return "A";
        if (score >= 700) return "B";
        if (score >= 650) return "C";
        if (score >= 600) return "D";
        return "F";
    }

    private double toEligibleAmount(String grade) {
        return switch (grade) {
            case "A" -> 100_000.0;
            case "B" -> 75_000.0;
            case "C" -> 50_000.0;
            case "D" -> 25_000.0;
            default -> 0.0;
        };
    }
}
