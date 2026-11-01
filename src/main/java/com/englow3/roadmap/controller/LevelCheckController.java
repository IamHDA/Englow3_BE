package com.englow3.roadmap.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.roadmap.dto.request.AttemptLinkRequest;
import com.englow3.roadmap.dto.request.StartLevelCheckRequest;
import com.englow3.roadmap.dto.response.LevelCheckResponse;
import com.englow3.roadmap.service.LevelCheckService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The learner's level checks. The paper is sat through the exam endpoints and the spoken answer through the assessment
 * endpoints; these keep the two together and report the combined band once both are graded.
 */
@RestController
@RequestMapping("/api/roadmap/level-checks")
@RequiredArgsConstructor
public class LevelCheckController {

    private final LevelCheckService levelCheckService;

    @PostMapping
    public ResponseEntity<LevelCheckResponse> start(@Valid @RequestBody StartLevelCheckRequest request) {
        return ResponseEntity
                .ok(LevelCheckResponse.from(levelCheckService.start(request.purpose(), request.milestoneId())));
    }

    /** 204 when the learner has never started one. */
    @GetMapping("/latest")
    public ResponseEntity<LevelCheckResponse> latest() {
        return levelCheckService.latest().map(LevelCheckResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{levelCheckId}")
    public ResponseEntity<LevelCheckResponse> get(@PathVariable UUID levelCheckId) {
        return ResponseEntity.ok(LevelCheckResponse.from(levelCheckService.get(levelCheckId)));
    }

    @PutMapping("/{levelCheckId}/exam-attempt")
    public ResponseEntity<LevelCheckResponse> attachExamAttempt(@PathVariable UUID levelCheckId,
            @Valid @RequestBody AttemptLinkRequest request) {
        return ResponseEntity
                .ok(LevelCheckResponse.from(levelCheckService.attachExamAttempt(levelCheckId, request.attemptId())));
    }

    @PutMapping("/{levelCheckId}/speaking-attempt")
    public ResponseEntity<LevelCheckResponse> attachSpeakingAttempt(@PathVariable UUID levelCheckId,
            @Valid @RequestBody AttemptLinkRequest request) {
        return ResponseEntity.ok(
                LevelCheckResponse.from(levelCheckService.attachSpeakingAttempt(levelCheckId, request.attemptId())));
    }

    @PostMapping("/{levelCheckId}/skip-speaking")
    public ResponseEntity<LevelCheckResponse> skipSpeaking(@PathVariable UUID levelCheckId) {
        return ResponseEntity.ok(LevelCheckResponse.from(levelCheckService.skipSpeaking(levelCheckId)));
    }
}
