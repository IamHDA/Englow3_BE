package com.englow3.generation.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.generation.dto.request.RequestGenerationRequest;
import com.englow3.generation.dto.response.GenerationAllowanceResponse;
import com.englow3.generation.dto.response.GenerationRequestResponse;
import com.englow3.generation.service.GenerationService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Generating content with AI. Staff and administrators may ask; what comes out are drafts sent for review, so approving
 * them stays the administrator's.
 */
@RestController
@RequestMapping("/api/admin/generation")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
public class AdminGenerationController {

    private final GenerationService service;

    @PostMapping("/requests")
    public ResponseEntity<GenerationRequestResponse> request(@Valid @RequestBody RequestGenerationRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenerationRequestResponse.from(service.request(r.kind(), r.brief())));
    }

    @GetMapping("/requests")
    public ResponseEntity<PageResponse<GenerationRequestResponse>> list(@PageableDefault(size = 20) Pageable page) {
        return ResponseEntity.ok(PageResponse.from(service.list(page).map(GenerationRequestResponse::from)));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<GenerationRequestResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(GenerationRequestResponse.from(service.detail(id)));
    }

    @GetMapping("/allowance")
    public ResponseEntity<GenerationAllowanceResponse> allowance() {
        return ResponseEntity.ok(GenerationAllowanceResponse.from(service.allowance()));
    }
}
