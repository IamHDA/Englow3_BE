package com.englow3.exam.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
import com.englow3.exam.dto.response.ExamMediaUrls;
import com.englow3.exam.dto.response.LearnerExamPaperResponse;
import com.englow3.exam.service.LearnerExamService;
import com.englow3.shared.page.PageResponse;
import com.englow3.shared.storage.ObjectStorageClient;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/exam-attempts")
public class ExamAttemptController {

    private final LearnerExamService learnerExamService;
    private final ExamMediaUrls mediaUrls;

    public ExamAttemptController(LearnerExamService learnerExamService, ObjectStorageClient objectStorage,
            @Value("${app.storage.exam-bucket}") String examBucket,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl) {
        this.learnerExamService = learnerExamService;
        this.mediaUrls = new ExamMediaUrls(objectStorage, examBucket, mediaUrlTtl);
    }

    /** The learner's own history, newest first. No review data - that is the result endpoint's job. */
    @GetMapping
    public ResponseEntity<PageResponse<ExamAttemptResponse>> history(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(PageResponse.from(learnerExamService.attemptHistory(pageable).map(ExamAttemptResponse::from)));
    }

    @GetMapping("/{id}/paper")
    public ResponseEntity<LearnerExamPaperResponse> getPaper(@PathVariable UUID id) {
        return ResponseEntity.ok(LearnerExamPaperResponse.from(learnerExamService.paperForAttempt(id), mediaUrls));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ExamAttemptResponse> submit(@PathVariable UUID id,
            @Valid @RequestBody SubmitExamAttemptRequest request) {
        return ResponseEntity.ok(ExamAttemptResponse.from(learnerExamService.submit(request.toCommand(id))));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<ExamAttemptResponse> result(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamAttemptResponse.from(learnerExamService.result(id)));
    }
}
