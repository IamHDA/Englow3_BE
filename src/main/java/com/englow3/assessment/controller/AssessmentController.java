package com.englow3.assessment.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.assessment.dto.request.AssessmentDraftRequest;
import com.englow3.assessment.dto.request.AssessmentStartRequest;
import com.englow3.assessment.dto.response.AssessmentAttemptResponse;
import com.englow3.assessment.dto.response.AssessmentCapabilitiesResponse;
import com.englow3.assessment.dto.response.AssessmentTaskResponse;
import com.englow3.assessment.dto.response.AssessmentUploadResponse;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.service.AssessmentService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {
    private final AssessmentService service;

    @GetMapping("/capabilities")
    public ResponseEntity<AssessmentCapabilitiesResponse> capabilities() {
        return ResponseEntity.ok(AssessmentCapabilitiesResponse.from(service.capabilities()));
    }

    @GetMapping("/tasks")
    public ResponseEntity<PageResponse<AssessmentTaskResponse>> tasks(
            @RequestParam(required = false) AssessmentSkill skill, @PageableDefault(size = 12) Pageable page) {
        return ResponseEntity.ok(PageResponse.from(service.catalog(skill, page).map(AssessmentTaskResponse::from)));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<AssessmentTaskResponse> task(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.task(id)));
    }

    @PostMapping("/tasks/{id}/attempts")
    public ResponseEntity<AssessmentUploadResponse> start(@PathVariable UUID id,
            @Valid @RequestBody AssessmentStartRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                AssessmentUploadResponse.from(service.start(id, r.clientKey(), r.contentType(), r.contentLength())));
    }

    @PutMapping("/attempts/{id}/draft")
    public ResponseEntity<AssessmentAttemptResponse> save(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDraftRequest r) {
        return ResponseEntity.ok(AssessmentAttemptResponse.from(service.saveDraft(id, r.answerText(), r.version())));
    }

    @PostMapping("/attempts/{id}/submit")
    public ResponseEntity<AssessmentAttemptResponse> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentAttemptResponse.from(service.submit(id)));
    }

    @PostMapping("/attempts/{id}/retry")
    public ResponseEntity<AssessmentAttemptResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentAttemptResponse.from(service.retry(id)));
    }

    @PostMapping("/attempts/{id}/request-review")
    public ResponseEntity<AssessmentAttemptResponse> review(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentAttemptResponse.from(service.requestReview(id)));
    }

    @GetMapping("/attempts/{id}")
    public ResponseEntity<AssessmentAttemptResponse> attempt(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentAttemptResponse.from(service.attempt(id)));
    }

    @GetMapping("/attempts")
    public ResponseEntity<PageResponse<AssessmentAttemptResponse>> history(@RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) AssessmentSkill skill,
            @RequestParam(required = false) AssessmentAttemptStatus status,
            @RequestParam(required = false) String title, @PageableDefault(size = 12) Pageable page) {
        return ResponseEntity.ok(PageResponse
                .from(service.history(taskId, skill, status, title, page).map(AssessmentAttemptResponse::from)));
    }
}
