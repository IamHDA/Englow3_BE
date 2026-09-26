package com.englow3.exam.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.request.SubmitExamAttemptRequest;
import com.englow3.exam.dto.response.ExamAttemptResponse;
import com.englow3.exam.dto.response.LearnerExamPaperResponse;
import com.englow3.exam.service.ExamAttemptService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exam-attempts")
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;

    public ExamAttemptController(ExamAttemptService examAttemptService) {
        this.examAttemptService = examAttemptService;
    }

    /** The learner's own history, newest first. No review data - that is the result endpoint's job. */
    @GetMapping
    public ResponseEntity<PageResponse<ExamAttemptResponse>> history(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(PageResponse.from(examAttemptService.attemptHistory(pageable).map(ExamAttemptResponse::from)));
    }

    @GetMapping("/{id}/paper")
    public ResponseEntity<LearnerExamPaperResponse> getPaper(@PathVariable UUID id) {
        return ResponseEntity.ok(LearnerExamPaperResponse.from(examAttemptService.paperForAttempt(id)));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ExamAttemptResponse> submit(@PathVariable UUID id,
            @Valid @RequestBody SubmitExamAttemptRequest request) {
        return ResponseEntity.ok(ExamAttemptResponse.from(examAttemptService.submit(request.toCommand(id))));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<ExamAttemptResponse> result(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamAttemptResponse.from(examAttemptService.result(id)));
    }
}
