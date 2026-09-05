package com.englow3.ai.foundation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@RequestHeader(name = "Last-Event-ID", required = false) Long lastEventId,
            @RequestParam(name = "after", required = false) Long after) {
        long cursor = lastEventId != null ? lastEventId : after == null ? 0 : after;
        return service.eventsForCurrentUser(cursor);
    }

    @DeleteMapping("/{jobId}")
    ResponseEntity<Void> cancel(@PathVariable UUID jobId) {
        service.cancelForCurrentUser(jobId);
        return ResponseEntity.noContent().build();
    }
}
