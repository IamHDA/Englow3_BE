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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @ApiResponse(responseCode = "201", description = "Speaking prompt created")
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

    @PostMapping("/prompts/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpeakingPromptReviewResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.restore(id)));
    }

    @PostMapping("/prompts/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<SpeakingPromptReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptReviewResponse.from(adminSpeakingService.archive(id)));
    }

    @GetMapping("/prompts/{id}/authoring")
    ResponseEntity<com.englow3.speaking.dto.response.AuthoringResponse> authoring(@PathVariable UUID id) {
        return ResponseEntity
                .ok(com.englow3.speaking.dto.response.AuthoringResponse.from(adminSpeakingService.authoringDetail(id)));
    }

    @PostMapping("/prompts/authoring")
    ResponseEntity<com.englow3.speaking.dto.response.AuthoringResponse> createAuthoring(
            @Valid @RequestBody com.englow3.speaking.dto.request.SaveAuthoringRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(com.englow3.speaking.dto.response.AuthoringResponse
                .from(adminSpeakingService.saveAuthoring(authoringCommand(null, request))));
    }

    @org.springframework.web.bind.annotation.PutMapping("/prompts/{id}/authoring")
    ResponseEntity<com.englow3.speaking.dto.response.AuthoringResponse> updateAuthoring(@PathVariable UUID id,
            @Valid @RequestBody com.englow3.speaking.dto.request.SaveAuthoringRequest request) {
        return ResponseEntity.ok(com.englow3.speaking.dto.response.AuthoringResponse
                .from(adminSpeakingService.saveAuthoring(authoringCommand(id, request))));
    }

    private com.englow3.speaking.dto.command.SaveAuthoringCommand authoringCommand(UUID id,
            com.englow3.speaking.dto.request.SaveAuthoringRequest r) {
        var m = r.metadata();
        return new com.englow3.speaking.dto.command.SaveAuthoringCommand(id, r.version(),
                new com.englow3.speaking.dto.command.CreateSpeakingPromptCommand(m.slug(), m.title(), m.category(),
                        m.targetLevel(), m.referenceText(), m.ipaTranscript(), m.translationVi(), m.phonemeTarget(),
                        m.tips()));
    }
}
