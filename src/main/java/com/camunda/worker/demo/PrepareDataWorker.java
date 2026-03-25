package com.camunda.worker.demo;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * STEP 1 — Prepare Demo Data
 *
 * <p>Sets up the shared variables used by all three looping patterns:
 *
 * <ul>
 *   <li>{@code items} — the collection iterated by all three patterns
 *   <li>{@code loopIndex} — starts at 0; the loop task advances it on each iteration
 *   <li>{@code loopDone} — set to {@code true} when loopIndex reaches items.size()
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrepareDataWorker {

    @JobWorker(type = "demo.prepareData", autoComplete = false)
    public void handle(final JobClient client, final ActivatedJob job) {
        log.info("[DEMO][PrepareData] type={} key={}", job.getType(), job.getKey());

        var items = List.of("Item-A", "Item-B", "Item-C");

        log.info("[DEMO][PrepareData] Prepared items={} loopIndex=0 loopDone=false", items);

        client.newCompleteCommand(job.getKey())
                .variables(Map.of(
                        "items", items,
                        "loopIndex", 0,
                        "loopDone", false))
                .send()
                .join();
    }
}
