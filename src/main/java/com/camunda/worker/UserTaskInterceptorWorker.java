package com.camunda.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Intercepts all Zeebe job-based user tasks (type: io.camunda.zeebe:userTask).
 *
 * When a user task becomes active, Zeebe creates a job of this type.
 * This worker captures that job key and writes it back as a process variable
 * (named after the BPMN element ID), then re-fails the job so Zeebe keeps
 * the task open for human completion via /api/tasks/{taskKey}/... endpoints.
 *
 * The stored variable lets you look up the job key from Operate without
 * needing to dig through the Zeebe API manually.
 *
 * Stored variable name pattern: {elementId}_jobKey
 *   e.g. task_approve_shipment_jobKey, task_approve_cancellation_jobKey,
 *        task_initiate_refund_jobKey
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class UserTaskInterceptorWorker {

    private final CamundaClient camundaClient;

    @JobWorker(type = "io.camunda.zeebe:userTask", autoComplete = false, timeout = 86400000)
    public void interceptUserTask(JobClient client, ActivatedJob job, @Variable(optional = true) String orderId) {

        var elementId = job.getElementId();
        var jobKey = job.getKey();
        var variableName = elementId + "_jobKey";

        log.info(
                "[UserTaskInterceptor] User task active — elementId={} jobKey={} orderId={}",
                elementId,
                jobKey,
                orderId);

        // Write the job key back as a process variable so it's visible in Operate
        // and can be retrieved without manual API queries.
        camundaClient
                .newSetVariablesCommand(job.getProcessInstanceKey())
                .variables(Map.of(variableName, jobKey))
                .local(false)
                .send()
                .join();

        // Do NOT complete the job — fail with max retries kept so Zeebe holds
        // the task open. The human completes it via /api/tasks/{jobKey}/...
        client.newFailCommand(jobKey)
                .retries(job.getRetries())
                .errorMessage("Awaiting human action — complete via /api/tasks/" + jobKey)
                .send()
                .join();
    }
}
