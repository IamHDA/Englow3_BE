package com.englow3.ai.foundation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/jobs")
class AiJobController {

    private final AiJobService service;

    AiJobController(AiJobService service) {
        this.service = service;
    }

    @GetMapping("/{jobId}")
    AiJobResponse get(@PathVariable UUID jobId) {
        return AiJobResponse.from(service.getForCurrentUser(jobId));
    }

    @DeleteMapping("/{jobId}")
    ResponseEntity<Void> cancel(@PathVariable UUID jobId) {
        service.cancelForCurrentUser(jobId);
        return ResponseEntity.noContent().build();
    }
}
