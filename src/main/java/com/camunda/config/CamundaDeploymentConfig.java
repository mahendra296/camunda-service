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
// @Component
@RequiredArgsConstructor
public class CamundaDeploymentConfig implements ApplicationRunner {

    private final CamundaClient camundaClient;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:workflow/*.bpmn");

        if (resources.length == 0) {
            log.warn("[CamundaDeployment] No BPMN files found in classpath:workflow/");
            return;
        }

        log.info("[CamundaDeployment] Found {} BPMN file(s) to deploy", resources.length);

        DeployResourceCommandStep1.DeployResourceCommandStep2 command = null;
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            log.info("[CamundaDeployment] Adding: {}", filename);
            if (command == null) {
                command = camundaClient.newDeployResourceCommand()
                        .addResourceFromClasspath("workflow/" + filename);
            } else {
                command = command.addResourceFromClasspath("workflow/" + filename);
            }
        }

        DeploymentEvent deployment = command.send().join();

        deployment.getProcesses().forEach(p ->
                log.info("[CamundaDeployment] Deployed: id={} version={} key={}",
                        p.getBpmnProcessId(), p.getVersion(), p.getProcessDefinitionKey()));
    }
}
