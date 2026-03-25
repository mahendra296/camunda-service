package com.camunda.worker.demo;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STEP 5 — Collect and compare results
 *
 * <p>Receives all three output collections and logs a side-by-side summary:
 *
 * <ul>
 *   <li>{@code loopResults} — manually accumulated by the XOR-gateway loop, one item per iteration
 *   <li>{@code sequentialResults} — auto-collected by Zeebe sequential MI, one-at-a-time
 *   <li>{@code parallelResults} — auto-collected by Zeebe parallel MI, all-at-once
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectResultsWorker {

    @JobWorker(type = "demo.collectResults", autoComplete = false)
    public void handle(
            final JobClient client,
            final ActivatedJob job,
            @Variable(optional = true) List<Object> loopResults,
            @Variable(optional = true) List<Object> sequentialResults,
            @Variable(optional = true) List<Object> parallelResults) {

        log.info("[DEMO][CollectResults] type={} key={}", job.getType(), job.getKey());

        log.info("══════════════════════════════════════════════════════");
        log.info("  DEMO SUMMARY");
        log.info("══════════════════════════════════════════════════════");
        log.info("  Loop results (XOR gateway, manual accumulation per index):");
        if (loopResults != null) {
            for (int i = 0; i < loopResults.size(); i++) {
                log.info("    [{}] {}", i + 1, loopResults.get(i));
            }
        }
        log.info("  Sequential results (Zeebe MI, one at a time, ordered):");
        if (sequentialResults != null) {
            for (int i = 0; i < sequentialResults.size(); i++) {
                log.info("    [{}] {}", i + 1, sequentialResults.get(i));
            }
        }
        log.info("  Parallel results (Zeebe MI, all at once, arrival order varies):");
        if (parallelResults != null) {
            for (int i = 0; i < parallelResults.size(); i++) {
                log.info("    [{}] {}", i + 1, parallelResults.get(i));
            }
        }
        log.info("══════════════════════════════════════════════════════");

        client.newCompleteCommand(job.getKey()).send().join();
    }
}
