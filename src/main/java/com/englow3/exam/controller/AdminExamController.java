package com.englow3.exam.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.request.CreateExamRequest;
import com.englow3.exam.dto.request.SearchExamRequest;
import com.englow3.exam.dto.request.UpdateExamRequest;
import com.englow3.exam.dto.response.ExamDetailResponse;
import com.englow3.exam.dto.response.ExamListItemResponse;
import com.englow3.exam.dto.response.ExamMediaUrls;
import com.englow3.exam.dto.response.ExamResponse;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.service.AdminExamService;
import com.englow3.shared.page.PageResponse;
import com.englow3.shared.storage.ObjectStorageClient;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/exams")
@PreAuthorize("hasRole('ADMIN')")
class AdminExamController {

    private final AdminExamService adminExamService;
    private final ObjectStorageClient objectStorage;
    private final Duration mediaUrlTtl;

    AdminExamController(AdminExamService adminExamService, ObjectStorageClient objectStorage,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl) {
        this.adminExamService = adminExamService;
        this.objectStorage = objectStorage;
        this.mediaUrlTtl = mediaUrlTtl;
    }

    @PostMapping
    ResponseEntity<ExamResponse> create(@Valid @RequestBody CreateExamRequest request) {
        CreateExamCommand command = new CreateExamCommand(request.title(), request.description(), request.examType(),
                request.certificateType(), request.certificateVariant(), request.targetLevel(),
                request.durationSeconds(), request.maxRawScore(), request.passScore());

        return ResponseEntity.status(HttpStatus.CREATED).body(ExamResponse.from(adminExamService.create(command)));
    }

    @GetMapping
    ResponseEntity<PageResponse<ExamListItemResponse>> search(@Valid SearchExamRequest request,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SearchExamCommand command = new SearchExamCommand(request.status(), request.examType(), request.title());

        return ResponseEntity
                .ok(PageResponse.from(adminExamService.search(command, pageable).map(ExamListItemResponse::from)));
    }

    /** The whole paper, media signed for an hour - long enough to read a full TOEIC paper before publishing it. */
    @GetMapping("/{id}")
    ResponseEntity<ExamDetailResponse> detail(@PathVariable UUID id) {
        ExamDetailResult result = adminExamService.detail(new ExamDetailCommand(id));

        return ResponseEntity.ok(ExamDetailResponse.from(result, ExamMediaUrls.of(objectStorage, mediaUrlTtl)));
    }

    @PutMapping("/{id}")
    ResponseEntity<ExamResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateExamRequest request) {
        UpdateExamCommand command = new UpdateExamCommand(id, request.title(), request.description(),
                request.examType(), request.certificateType(), request.certificateVariant(), request.targetLevel(),
                request.durationSeconds(), request.maxRawScore(), request.passScore());

        return ResponseEntity.ok(ExamResponse.from(adminExamService.update(command)));
    }

    @PostMapping("/{id}/publish")
    ResponseEntity<ExamResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(adminExamService.publish(new PublishExamCommand(id))));
    }

    @PostMapping("/{id}/archive")
    ResponseEntity<ExamResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(adminExamService.archive(new ArchiveExamCommand(id))));
    }
}
