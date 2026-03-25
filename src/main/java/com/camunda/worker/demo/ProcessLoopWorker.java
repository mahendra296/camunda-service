package com.camunda.worker.demo;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PATTERN 1 — Loop via XOR gateway
 *
 * <p>How it works:
 *
 * <ol>
 *   <li>This worker fires once per loop iteration.
 *   <li>It reads {@code items[loopIndex]}, processes the current item, then increments the index.
 *   <li>It sets {@code loopDone = (loopIndex >= items.size())}.
 *   <li>The XOR gateway reads {@code loopDone}:
 *       <ul>
 *         <li>{@code false} → token goes back to this task (next item)
 *         <li>{@code true} → all items processed, token moves forward
 *       </ul>
 * </ol>
 *
 * <p>Key difference from multi-instance: only ONE job exists at any moment.
 * The loop is driven by process flow, not by Zeebe's built-in collection iteration.
 * Results are manually accumulated into {@code loopResults} on each iteration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessLoopWorker {

    @JobWorker(type = "demo.processLoop", autoComplete = false)
    public void handle(
            final JobClient client,
            final ActivatedJob job,
            @Variable List<String> items,
            @Variable Integer loopIndex,
            @Variable(optional = true) List<Object> loopResults) {

        var currentItem = items.get(loopIndex);
        var nextIndex = loopIndex + 1;
        var done = nextIndex >= items.size();

        log.info(
                "[DEMO][Loop] type={} key={} | item={} ({}/{}) | loopDone={}",
                job.getType(),
                job.getKey(),
                currentItem,
                nextIndex,
                items.size(),
                done);

        var result = Map.of(
                "item", currentItem,
                "pattern", "loop",
                "index", loopIndex,
                "processedAt", System.currentTimeMillis());

        var accumulated = new ArrayList<>(loopResults != null ? loopResults : List.of());
        accumulated.add(result);

        if (done) {
            log.info("[DEMO][Loop] All {} items processed — gateway will route forward.", items.size());
        } else {
            log.info("[DEMO][Loop] Next item='{}' — gateway will send token back.", items.get(nextIndex));
        }

        var vars = new HashMap<String, Object>();
        vars.put("loopIndex", nextIndex);
        vars.put("loopDone", done);
        vars.put("loopResults", accumulated);

        client.newCompleteCommand(job.getKey())
                .variables(vars)
                .send()
                .join();
    }
}
