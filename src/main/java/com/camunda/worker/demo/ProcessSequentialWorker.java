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
 * PATTERN 2 — Sequential Multi-Instance
 *
 * <p>How it works:
 *
 * <ol>
 *   <li>Zeebe reads {@code inputCollection="=items"} → ["Item-A", "Item-B", "Item-C"].
 *   <li>It creates a job for Item-A. This worker picks it up and completes it.
 *   <li>Only then does Zeebe create the job for Item-B — and so on.
 *   <li>After all items are processed, Zeebe collects every {@code sequentialResult}
 *       into {@code sequentialResults} and the token moves to the next task.
 * </ol>
 *
 * <p>The variable {@code currentItem} is injected automatically by Zeebe from the
 * {@code inputElement} declaration in the BPMN — no manual ioMapping needed.
 *
 * <p>Key difference from parallel: you will see the log lines appear one-by-one,
 * Item-A before Item-B before Item-C.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessSequentialWorker {

    @JobWorker(type = "demo.processSequential", autoComplete = false)
    public void handle(final JobClient client, final ActivatedJob job, @Variable String currentItem) {

        log.info(
                "[DEMO][Sequential] type={} key={} | processing item='{}' (one at a time)",
                job.getType(),
                job.getKey(),
                currentItem);

        var result = Map.of("item", currentItem, "pattern", "sequential", "processedAt", System.currentTimeMillis());

        log.info("[DEMO][Sequential] Completed item='{}' result={}", currentItem, result);

        client.newCompleteCommand(job.getKey())
                .variables(Map.of("sequentialResult", result))
                .send()
                .join();
    }
}
