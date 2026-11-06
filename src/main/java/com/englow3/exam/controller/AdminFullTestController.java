package com.englow3.exam.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.request.RejectExamRequest;
import com.englow3.exam.dto.request.SaveFullTestRequest;
import com.englow3.exam.dto.response.FullTestResponse;
import com.englow3.exam.service.FullTestService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** Full test authoring and review: the same roles and the same workflow as papers. */
@RestController
@RequestMapping("/api/admin/full-tests")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
class AdminFullTestController {

    private final FullTestService fullTests;

    AdminFullTestController(FullTestService fullTests) {
        this.fullTests = fullTests;
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Full test created as a draft")
    ResponseEntity<FullTestResponse> create(@Valid @RequestBody SaveFullTestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FullTestResponse.from(fullTests.save(request.toCommand(null))));
    }

    @PutMapping("/{id}")
    ResponseEntity<FullTestResponse> update(@PathVariable UUID id, @Valid @RequestBody SaveFullTestRequest request) {
        return ResponseEntity.ok(FullTestResponse.from(fullTests.save(request.toCommand(id))));
    }

    @GetMapping
    ResponseEntity<List<FullTestResponse>> list() {
        return ResponseEntity.ok(fullTests.list().stream().map(FullTestResponse::from).toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<FullTestResponse> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(FullTestResponse.from(fullTests.detail(id)));
    }

    @PostMapping("/{id}/submit-for-review")
    ResponseEntity<FullTestResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(FullTestResponse.from(fullTests.submitForReview(id)));
    }

    /** Approving publishes, as it does for a paper. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<FullTestResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(FullTestResponse.from(fullTests.approve(id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<FullTestResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectExamRequest request) {
        return ResponseEntity.ok(FullTestResponse.from(fullTests.reject(id, request.note())));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<FullTestResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(FullTestResponse.from(fullTests.archive(id)));
    }
}
