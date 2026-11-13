package com.englow3.exam.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.response.FullTestResponse;
import com.englow3.exam.dto.response.FullTestSittingResponse;
import com.englow3.exam.service.FullTestService;
import com.englow3.exam.service.FullTestSittingService;

/**
 * The learner's side of the IELTS full test. The parts are sat on their own screens - the paper through
 * {@code /api/exam-attempts}, the five tasks through the Writing & Speaking API - and a sitting picks them up.
 */
@RestController
public class FullTestController {

    private final FullTestService fullTests;
    private final FullTestSittingService sittings;

    public FullTestController(FullTestService fullTests, FullTestSittingService sittings) {
        this.fullTests = fullTests;
        this.sittings = sittings;
    }

    @GetMapping("/api/full-tests")
    public ResponseEntity<List<FullTestResponse>> catalogue() {
        return ResponseEntity.ok(fullTests.catalogue().stream().map(FullTestResponse::from).toList());
    }

    /** Starts a sitting, or hands back the one still open. */
    @PostMapping("/api/full-tests/{id}/sittings")
    public ResponseEntity<FullTestSittingResponse> start(@PathVariable UUID id) {
        return ResponseEntity.ok(FullTestSittingResponse.from(sittings.start(id)));
    }

    @GetMapping("/api/full-test-sittings")
    public ResponseEntity<List<FullTestSittingResponse>> mine() {
        return ResponseEntity.ok(sittings.mine().stream().map(FullTestSittingResponse::from).toList());
    }

    @GetMapping("/api/full-test-sittings/{id}")
    public ResponseEntity<FullTestSittingResponse> view(@PathVariable UUID id) {
        return ResponseEntity.ok(FullTestSittingResponse.from(sittings.view(id)));
    }
}
