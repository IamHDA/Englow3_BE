package com.englow3.tutor.controller;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.englow3.shared.page.PageResponse;
import com.englow3.tutor.dto.command.*;
import com.englow3.tutor.dto.result.*;
import com.englow3.tutor.service.TutorBackOfficeService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tutor")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
public class TutorBackOfficeController {
    private final TutorBackOfficeService service;
    private final com.englow3.user.api.UserDirectory users;

    private final com.englow3.ai.api.AiJobQueue jobs;

    @GetMapping("/operations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.englow3.ai.api.AiJobQueue.Operations> operations() {
        return ResponseEntity.ok(jobs.operations());
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<com.englow3.user.api.UserDirectory.StaffMember>> staff() {
        return ResponseEntity.ok(users.backOfficeUsers());
    }

    @GetMapping("/resources")
    public ResponseEntity<PageResponse<TutorResourceResult>> resources(@RequestParam(defaultValue = "") String status,
            @PageableDefault(size = 15) Pageable page) {
        return ResponseEntity.ok(PageResponse.from(service.resources(status, page)));
    }

    @GetMapping("/resources/{id}")
    public ResponseEntity<TutorResourceResult> resource(@PathVariable UUID id) {
        return ResponseEntity.ok(service.resource(id));
    }

    @PostMapping("/resources")
    public ResponseEntity<TutorResourceResult> create(@Valid @RequestBody TutorResourceCommand command) {
        return ResponseEntity.status(201).body(service.create(command));
    }

    @PutMapping("/resources/{id}")
    public ResponseEntity<TutorResourceResult> update(@PathVariable UUID id,
            @Valid @RequestBody TutorResourceCommand command) {
        return ResponseEntity.ok(service.update(id, command));
    }

    @PostMapping("/resources/{id}/actions")
    public ResponseEntity<TutorResourceResult> resourceAction(@PathVariable UUID id,
            @Valid @RequestBody TutorReviewCommand command) {
        return ResponseEntity.ok(service.resourceAction(id, command));
    }

    @GetMapping("/reports")
    public ResponseEntity<PageResponse<TutorReportResult>> reports(@RequestParam(defaultValue = "") String status,
            @PageableDefault(size = 15) Pageable page) {
        return ResponseEntity.ok(PageResponse.from(service.reports(status, page)));
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<TutorReportResult> report(@PathVariable UUID id) {
        return ResponseEntity.ok(service.report(id));
    }

    @PostMapping("/reports/{id}/actions")
    public ResponseEntity<TutorReportResult> reportAction(@PathVariable UUID id,
            @Valid @RequestBody TutorReviewCommand command) {
        return ResponseEntity.ok(service.reportAction(id, command));
    }
}
