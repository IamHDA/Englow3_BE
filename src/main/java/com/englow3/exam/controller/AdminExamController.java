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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.request.CreateExamRequest;
import com.englow3.exam.dto.request.SearchExamRequest;
import com.englow3.exam.dto.response.ExamListItemResponse;
import com.englow3.exam.dto.response.ExamResponse;
import com.englow3.exam.service.AdminExamService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/exams")
@RequiredArgsConstructor
class AdminExamController {

    private final AdminExamService adminExamService;

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

    @PostMapping("/{id}/publish")
    ResponseEntity<ExamResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(adminExamService.publish(new PublishExamCommand(id))));
    }

    @PostMapping("/{id}/archive")
    ResponseEntity<ExamResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(adminExamService.archive(new ArchiveExamCommand(id))));
    }
}
