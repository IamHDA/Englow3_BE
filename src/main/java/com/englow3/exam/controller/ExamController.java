package com.englow3.exam.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.response.ExamAttemptResponse;
import com.englow3.exam.dto.response.LearnerExamResponse;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.service.LearnerExamService;
import com.englow3.shared.page.PageResponse;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final LearnerExamService learnerExamService;

    public ExamController(LearnerExamService learnerExamService) {
        this.learnerExamService = learnerExamService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<LearnerExamResponse>> search(@RequestParam(required = false) ExamType examType,
            @RequestParam(required = false) CertificateType certificateType,
            @RequestParam(required = false) CertificateVariant certificateVariant,
            @RequestParam(required = false) TargetLevel targetLevel, @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                learnerExamService.search(examType, certificateType, certificateVariant, targetLevel, title, pageable)
                        .map(LearnerExamResponse::from)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LearnerExamResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(LearnerExamResponse.from(learnerExamService.detail(id)));
    }

    @PostMapping("/{id}/attempts")
    public ResponseEntity<ExamAttemptResponse> startAttempt(@PathVariable UUID id) {
        ExamAttemptResult result = learnerExamService.start(id);
        return ResponseEntity.status(result.resumed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ExamAttemptResponse.from(result));
    }
}
