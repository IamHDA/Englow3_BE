package com.englow3.translation.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.shared.page.PageResponse;
import com.englow3.shared.page.StablePage;
import com.englow3.translation.dto.request.RejectTranslationSetRequest;
import com.englow3.translation.dto.request.SaveTranslationSetRequest;
import com.englow3.translation.dto.response.TranslationAuthoringResponse;
import com.englow3.translation.dto.response.TranslationSetReviewResponse;
import com.englow3.translation.entity.TranslationSetStatus;
import com.englow3.translation.service.AdminTranslationService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Authoring and review, split the same way as every other content type: staff write and submit, administrators decide.
 */
@RestController
@RequestMapping("/api/admin/translation")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminTranslationController {

    private final AdminTranslationService adminTranslationService;

    /** Every status, so the review queue has something to show. A null status means all of them. */
    @GetMapping("/sets")
    ResponseEntity<PageResponse<TranslationSetReviewResponse>> search(
            @RequestParam(required = false) TranslationSetStatus status, @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                adminTranslationService.searchForAuthoring(status, title, StablePage.of(pageable, Sort.by("title")))
                        .map(TranslationSetReviewResponse::from)));
    }

    @PostMapping("/sets/{id}/submit-for-review")
    ResponseEntity<TranslationSetReviewResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationSetReviewResponse.from(adminTranslationService.submitForReview(id)));
    }

    @PostMapping("/sets/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TranslationSetReviewResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationSetReviewResponse.from(adminTranslationService.approve(id)));
    }

    @PostMapping("/sets/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TranslationSetReviewResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectTranslationSetRequest request) {
        return ResponseEntity.ok(TranslationSetReviewResponse.from(adminTranslationService.reject(id, request.note())));
    }

    @PostMapping("/sets/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TranslationSetReviewResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationSetReviewResponse.from(adminTranslationService.publish(id)));
    }

    @PostMapping("/sets/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TranslationSetReviewResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationSetReviewResponse.from(adminTranslationService.restore(id)));
    }

    @PostMapping("/sets/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<TranslationSetReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationSetReviewResponse.from(adminTranslationService.archive(id)));
    }

    @GetMapping("/sets/{id}/authoring")
    ResponseEntity<TranslationAuthoringResponse> authoring(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationAuthoringResponse.from(adminTranslationService.authoringDetail(id)));
    }

    @PostMapping("/sets/authoring")
    @ApiResponse(responseCode = "201", description = "Translation set created")
    ResponseEntity<TranslationAuthoringResponse> createAuthoring(
            @Valid @RequestBody SaveTranslationSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                TranslationAuthoringResponse.from(adminTranslationService.saveAuthoring(request.toCommand(null))));
    }

    @PutMapping("/sets/{id}/authoring")
    ResponseEntity<TranslationAuthoringResponse> updateAuthoring(@PathVariable UUID id,
            @Valid @RequestBody SaveTranslationSetRequest request) {
        return ResponseEntity
                .ok(TranslationAuthoringResponse.from(adminTranslationService.saveAuthoring(request.toCommand(id))));
    }
}
