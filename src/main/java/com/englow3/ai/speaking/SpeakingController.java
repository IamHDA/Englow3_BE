package com.englow3.ai.speaking;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/speaking/sessions")
@Validated
class SpeakingController {

    private final SpeakingSessionService service;

    SpeakingController(SpeakingSessionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SpeakingDtos.CreateSessionResponse create(@Valid @RequestBody SpeakingDtos.CreateSessionRequest request) {
        return service.create(request);
    }

    @PostMapping("/{sessionId}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SpeakingDtos.SubmitSessionResponse submit(@PathVariable UUID sessionId,
            @Valid @RequestBody SpeakingDtos.SubmitSessionRequest request) {
        return service.submit(sessionId, request.idempotencyKey());
    }

    @GetMapping("/{sessionId}")
    SpeakingDtos.SessionResult result(@PathVariable UUID sessionId) {
        return service.result(sessionId);
    }

    @GetMapping
    List<SpeakingDtos.SessionSummary> history() {
        return service.history();
    }

    @GetMapping("/progress")
    SpeakingDtos.ProgressResponse progress(@RequestParam(defaultValue = "30") int windowDays) {
        return service.progress(windowDays);
    }

    @GetMapping("/errors")
    List<SpeakingDtos.RecurringError> recurringErrors() {
        return service.recurringErrors();
    }

    @DeleteMapping("/{sessionId}/recording")
    ResponseEntity<Void> delete(@PathVariable UUID sessionId) {
        service.delete(sessionId);
        return ResponseEntity.noContent().build();
    }
}
