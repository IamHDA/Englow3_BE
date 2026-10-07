package com.englow3.assessment.controller;

import java.util.UUID;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.dto.request.*;
import com.englow3.assessment.dto.response.*;
import com.englow3.assessment.entity.*;
import com.englow3.assessment.service.AssessmentAuthoringService;
import com.englow3.shared.page.PageResponse;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/assessments")
@PreAuthorize("hasAnyRole('STAFF','ADMIN')")
@RequiredArgsConstructor
public class AssessmentAuthoringController {
    private final AssessmentAuthoringService service;

    @GetMapping("/workload")
    public ResponseEntity<AssessmentWorkloadResponse> workload() {
        return ResponseEntity.ok(AssessmentWorkloadResponse.from(service.workload()));
    }

    @GetMapping("/tasks")
    public ResponseEntity<PageResponse<AssessmentTaskResponse>> tasks(
            @RequestParam(required = false) AssessmentSkill skill,
            @RequestParam(required = false) AssessmentTaskStatus status, @PageableDefault(size = 12) Pageable page) {
        return ResponseEntity
                .ok(PageResponse.from(service.tasks(skill, status, page).map(AssessmentTaskResponse::from)));
    }

    @PostMapping("/tasks")
    public ResponseEntity<AssessmentTaskResponse> create(@Valid @RequestBody AssessmentTaskRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AssessmentTaskResponse.from(service.create(command(r))));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<AssessmentTaskResponse> task(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.taskDetail(id)));
    }

    @GetMapping("/submissions/{id}/reviews")
    public ResponseEntity<java.util.List<AssessmentReviewResponse>> reviews(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reviews(id).stream().map(AssessmentReviewResponse::from).toList());
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<AssessmentTaskResponse> edit(@PathVariable UUID id, @RequestParam long version,
            @Valid @RequestBody AssessmentTaskRequest r) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.edit(id, command(r), version)));
    }

    @PostMapping("/tasks/{id}/submit")
    public ResponseEntity<AssessmentTaskResponse> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.transition(id, "submit", null)));
    }

    @PostMapping("/tasks/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssessmentTaskResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.transition(id, "approve", null)));
    }

    @PostMapping("/tasks/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssessmentTaskResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody AssessmentNoteRequest r) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.transition(id, "reject", r.note())));
    }

    @PostMapping("/tasks/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssessmentTaskResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.transition(id, "restore", null)));
    }

    @PostMapping("/tasks/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssessmentTaskResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentTaskResponse.from(service.transition(id, "archive", null)));
    }

    @GetMapping("/submissions")
    public ResponseEntity<PageResponse<AssessmentSubmissionSummaryResponse>> submissions(
            @RequestParam(required = false) AssessmentAttemptStatus status,
            @RequestParam(required = false) AssessmentSkill skill, @RequestParam(defaultValue = "") String term,
            @RequestParam(defaultValue = "true") boolean oldest, @PageableDefault(size = 12) Pageable page) {
        if (term.length() > 200)
            throw new com.englow3.shared.error.BadRequestException("SEARCH_TOO_LONG",
                    "Search must be at most 200 characters");
        return ResponseEntity.ok(PageResponse.from(service.searchSubmissions(status, skill, term, oldest, page)
                .map(AssessmentSubmissionSummaryResponse::from)));
    }

    @GetMapping("/submissions/{id}")
    public ResponseEntity<AssessmentAttemptResponse> submission(@PathVariable UUID id) {
        return ResponseEntity.ok(AssessmentAttemptResponse.from(service.submission(id)));
    }

    @PostMapping("/submissions/{id}/grade")
    public ResponseEntity<AssessmentAttemptResponse> grade(@PathVariable UUID id,
            @Valid @RequestBody AssessmentReviewRequest r) {
        return ResponseEntity.ok(
                AssessmentAttemptResponse.from(service.grade(id, r.report(), r.note(), r.transcript(), r.version())));
    }

    private AssessmentTaskCommand command(AssessmentTaskRequest r) {
        return new AssessmentTaskCommand(r.skill(), r.title(), r.taskType(), r.instructions(), r.rubricNotes(),
                r.sampleAnswer(), r.minimumWords(), r.timeLimitSeconds());
    }
}
