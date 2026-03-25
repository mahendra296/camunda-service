package com.camunda.service;

import com.camunda.dto.StartDemoResponse;
import io.camunda.client.CamundaClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoProcessService {

    private final CamundaClient camundaClient;

    public StartDemoResponse startProcess() {
        log.info("[DemoProcess] Starting multi-instance-demo-process");

        var event = camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId("multi-instance-demo-process")
                .latestVersion()
                .variables(Map.of("startedAt", System.currentTimeMillis()))
                .send()
                .join();

        log.info("[DemoProcess] Started. instanceKey={}", event.getProcessInstanceKey());

        return new StartDemoResponse(
                event.getProcessInstanceKey(),
                "STARTED",
                "Watch the logs: loop fires 3×, then sequential (Item-A→B→C), then parallel (all at once)");
    }
}
