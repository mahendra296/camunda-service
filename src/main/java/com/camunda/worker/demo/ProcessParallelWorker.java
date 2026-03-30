package com.camunda.worker.demo;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PATTERN 3 — Parallel Multi-Instance
 *
 * <p>How it works:
 *
 * <ol>
 *   <li>Zeebe reads {@code inputCollection="=items"} → ["Item-A", "Item-B", "Item-C"].
 *   <li>It creates ALL THREE jobs at the same time — one per item.
 *   <li>This worker picks up whichever job is available; the order is non-deterministic.
 *   <li>After every parallel instance completes, Zeebe collects each {@code parallelResult}
 *       into {@code parallelResults} and the token moves on.
 * </ol>
 *
 * <p>Key difference from sequential: in the logs you will see Item-A, Item-B, and Item-C
 * log lines interleaved — they run at the same time.
 * The task completes only when the LAST parallel instance finishes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessParallelWorker {

    @JobWorker(type = "demo.processParallel", autoComplete = false)
    public void handle(final JobClient client, final ActivatedJob job, @Variable String currentItem) {

        log.info(
                "[DEMO][Parallel] type={} key={} | processing item='{}' (all items run simultaneously)",
                job.getType(),
                job.getKey(),
                currentItem);

        var result = Map.of("item", currentItem, "pattern", "parallel", "processedAt", System.currentTimeMillis());

        log.info("[DEMO][Parallel] Completed item='{}' result={}", currentItem, result);

        client.newCompleteCommand(job.getKey())
                .variables(Map.of("parallelResult", result))
                .send()
                .join();
    }
}
