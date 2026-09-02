package com.camunda.process;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobClient;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class MultiInstanceDemoProcessTest {

    private static final String PROCESS_ID = "multi-instance-demo-process";
    private static final List<String> ITEMS = List.of("item-1", "item-2", "item-3");

    private CamundaClient client;
    private CamundaProcessTestContext processTestContext;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("workflow/multi-instance-demo-process.bpmn")
                .send()
                .join();
    }

    private ProcessInstanceEvent startDemo() {
        processTestContext.mockJobWorker("demo.prepareData").thenComplete(Map.of("items", ITEMS));
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .send()
                .join();
    }

    @Test
    void loopRepeatsUntilDone_thenProcessesSequentialItem() {
        var callCount = new AtomicInteger();
        processTestContext.mockJobWorker("demo.processLoop").withHandler((JobClient jobClient, ActivatedJob job) -> {
            boolean done = callCount.incrementAndGet() >= 2;
            jobClient
                    .newCompleteCommand(job)
                    .variables(Map.of("loopDone", done))
                    .send()
                    .join();
        });
        processTestContext.mockJobWorker("demo.processSequential").thenComplete(Map.of("sequentialResult", "done"));
        processTestContext.mockJobWorker("demo.processParallel").thenComplete(Map.of("parallelResult", "done"));
        processTestContext.mockJobWorker("demo.collectResults").thenComplete();

        var instance = startDemo();

        assertThat(instance)
                .isCompleted()
                .hasCompletedElement("task_process_loop", 2)
                .hasCompletedElements(
                        "task_process_sequential",
                        "task_process_parallel",
                        "task_collect_results",
                        "end_demo_complete");
    }

    @Test
    void sequentialMultiInstance_processesEveryItem() {
        processTestContext.mockJobWorker("demo.processLoop").thenComplete(Map.of("loopDone", true));
        processTestContext.mockJobWorker("demo.processSequential").thenComplete(Map.of("sequentialResult", "done"));
        processTestContext.mockJobWorker("demo.processParallel").thenComplete(Map.of("parallelResult", "done"));
        processTestContext.mockJobWorker("demo.collectResults").thenComplete();

        var instance = startDemo();

        // Element-instance counts for a multi-instance activity include the multi-instance body
        // itself (ITEMS.size() + 1), so the output collection is the reliable per-item signal.
        assertThat(instance)
                .isCompleted()
                .hasVariableSatisfies(
                        "sequentialResults",
                        List.class,
                        results -> assertThat(results).hasSize(ITEMS.size()));
    }

    @Test
    void parallelMultiInstance_processesEveryItem() {
        processTestContext.mockJobWorker("demo.processLoop").thenComplete(Map.of("loopDone", true));
        processTestContext.mockJobWorker("demo.processSequential").thenComplete(Map.of("sequentialResult", "done"));
        processTestContext.mockJobWorker("demo.processParallel").thenComplete(Map.of("parallelResult", "done"));
        processTestContext.mockJobWorker("demo.collectResults").thenComplete();

        var instance = startDemo();

        assertThat(instance)
                .isCompleted()
                .hasVariableSatisfies(
                        "parallelResults",
                        List.class,
                        results -> assertThat(results).hasSize(ITEMS.size()));
    }
}
