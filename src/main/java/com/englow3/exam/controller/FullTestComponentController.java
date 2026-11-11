package com.englow3.exam.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.englow3.exam.service.FullTestComponentService;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.dto.request.*;
import com.englow3.exam.dto.response.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/full-test-sittings/{id}")
public class FullTestComponentController {
    private final FullTestComponentService service;

    @PostMapping("/paper")
    public ResponseEntity<ExamAttemptResponse> paper(@PathVariable UUID id,
            @RequestBody(required = false) StartFullTestPaperRequest request) {
        return ResponseEntity
                .ok(ExamAttemptResponse.from(service.startPaper(id, request == null ? null : request.onOpen())));
    }

    @PostMapping("/parts/{slot}")
    public ResponseEntity<FullTestPartStartResponse> assessment(@PathVariable UUID id, @PathVariable FullTest.Slot slot,
            @Valid @RequestBody StartFullTestAssessmentRequest request) {
        return ResponseEntity.ok(FullTestPartStartResponse.from(
                service.startAssessment(id, slot, request.taskId(), request.contentType(), request.contentLength())));
    }
}
