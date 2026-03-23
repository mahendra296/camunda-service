package com.camunda.config;

import io.camunda.client.CamundaClient;
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
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:workflow/*.bpmn");

        /*if (resources.length == 0) {
            log.warn("[CamundaDeployment] No BPMN files found in classpath:workflow/");
            return;
        }

        log.info("[CamundaDeployment] Found {} BPMN file(s) to deploy", resources.length);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                DeployResourceCommandStep1.DeployResourceCommandStep2 command = null;
                for (Resource resource : resources) {
                    String filename = resource.getFilename();
                    log.info("[CamundaDeployment] Adding: {}", filename);
                    if (command == null) {
                        command = camundaClient
                                .newDeployResourceCommand()
                                .addResourceFromClasspath("workflow/" + filename);
                    } else {
                        command = command.addResourceFromClasspath("workflow/" + filename);
                    }
                }

                DeploymentEvent deployment = command.send().join();
                deployment
                        .getProcesses()
                        .forEach(p -> log.info(
                                "[CamundaDeployment] Deployed: id={} version={} key={}",
                                p.getBpmnProcessId(),
                                p.getVersion(),
                                p.getProcessDefinitionKey()));
                return;

            } catch (Exception e) {
                log.warn(
                        "[CamundaDeployment] Attempt {}/{} failed — Zeebe not ready yet: {}",
                        attempt,
                        MAX_RETRIES,
                        e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("[CamundaDeployment] All retries exhausted. BPMN deployment failed.", e);
                    return;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }*/
    }
}
