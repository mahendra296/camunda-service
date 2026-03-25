package com.camunda.controller;

import com.camunda.dto.StartDemoResponse;
import com.camunda.service.DemoProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoProcessController {

    private final DemoProcessService demoProcessService;

    @PostMapping("/start")
    public ResponseEntity<StartDemoResponse> start() {
        return ResponseEntity.ok(demoProcessService.startProcess());
    }
}
