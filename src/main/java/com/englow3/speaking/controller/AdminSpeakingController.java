package com.englow3.speaking.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.shared.page.PageResponse;
import com.englow3.speaking.dto.command.CreateSpeakingPromptCommand;
import com.englow3.speaking.dto.request.CreateSpeakingPromptRequest;
import com.englow3.speaking.dto.request.RejectSpeakingPromptRequest;
import com.englow3.speaking.dto.response.SpeakingPromptReviewResponse;
import com.englow3.speaking.entity.SpeakingPromptStatus;
import com.englow3.speaking.service.AdminSpeakingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Authoring and review, split the same way as every other content type: staff write and submit, administrators decide.
 */
@RestController
@RequestMapping("/api/admin/speaking")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminSpeakingController {

    private final AdminSpeakingService adminSpeakingService;

    @PostMapping("/prompts")
    ResponseEntity<SpeakingPromptReviewResponse> create(@Valid @RequestBody CreateSpeakingPromptRequest request) {
        CreateSpeakingPromptCommand command = new CreateSpeakingPromptCommand(request.slug(), request.title(),
                request.category(), request.targetLevel(), request.referenceText(), request.ipaTranscript(),
                request.translationVi(), request.phonemeTarget(), request.tips());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SpeakingPromptReviewResponse.from(adminSpeakingService.create(command)));
    }

    /** Every status, so the review queue has something to show. A null status means all of them. */
    @GetMapping("/prompts")
    ResponseEntity<PageResponse<SpeakingPromptReviewResponse>> search(
            @RequestParam(required = false) SpeakingPromptStatus status, @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(adminSpeakingService.searchForAuthoring(status, title, pageable)
                .map(SpeakingPromptReviewResponse::from)));
    }

    @PostMapping("/prompts/{id}/submit-for-review")
    ResponseEntity<SpeakingPromptReviewResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.submitForReview(id)));
    }

    @PostMapping("/prompts/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpeakingPromptReviewResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.approve(id)));
    }

    @PostMapping("/prompts/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpeakingPromptReviewResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectSpeakingPromptRequest request) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.reject(id, request.note())));
    }

    @PostMapping("/prompts/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpeakingPromptReviewResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.publish(id)));
    }

    @PostMapping("/prompts/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpeakingPromptReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.archive(id)));
    }
}
