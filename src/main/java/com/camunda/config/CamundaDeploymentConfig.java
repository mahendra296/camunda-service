/*
package com.camunda.config;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CamundaDeploymentConfig implements ApplicationRunner {

    private static final int MAX_RETRIES = 10;
    private static final long RETRY_DELAY_MS = 5_000;

    private final CamundaClient camundaClient;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();

        // Collect BPMN and DMN resources — both must be in the same deployment so that
        // Business Rule Tasks using bindingType="deployment" can resolve their DMN decisions.
        var bpmnResources = resolver.getResources("classpath:workflow/*.bpmn");
        var dmnResources = resolver.getResources("classpath:workflow/*.dmn");

        var allResources = new java.util.ArrayList<Resource>();
        java.util.Collections.addAll(allResources, bpmnResources);
        java.util.Collections.addAll(allResources, dmnResources);

        if (allResources.isEmpty()) {
            log.warn("[CamundaDeployment] No BPMN/DMN files found in classpath:workflow/");
            return;
        }

        log.info("[CamundaDeployment] Found {} resource(s) to deploy", allResources.size());
        allResources.forEach(r -> log.info("[CamundaDeployment] Resource: {}", r.getFilename()));

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                DeployResourceCommandStep1.DeployResourceCommandStep2 command = null;
                for (Resource resource : allResources) {
                    String filename = resource.getFilename();
                    if (command == null) {
                        command = camundaClient
                                .newDeployResourceCommand()
                                .addResourceFromClasspath("workflow/" + filename);
                    } else {
                        command = command.addResourceFromClasspath("workflow/" + filename);
                    }
                }

                DeploymentEvent deployment = command.send().join();

                deployment.getProcesses().forEach(p -> log.info(
                        "[CamundaDeployment] Process deployed: id={} version={} key={}",
                        p.getBpmnProcessId(),
                        p.getVersion(),
                        p.getProcessDefinitionKey()));

                deployment.getDecisions().forEach(d -> log.info(
                        "[CamundaDeployment] Decision deployed: id={} version={} key={}",
                        d.getDmnDecisionId(),
                        d.getVersion(),
                        d.getDecisionKey()));

                return;

            } catch (Exception e) {
                log.warn(
                        "[CamundaDeployment] Attempt {}/{} failed — Zeebe not ready yet: {}",
                        attempt,
                        MAX_RETRIES,
                        e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("[CamundaDeployment] All retries exhausted. Deployment failed.", e);
                    return;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
*/
