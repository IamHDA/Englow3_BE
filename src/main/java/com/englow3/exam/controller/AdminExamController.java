package com.englow3.exam.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.ApproveExamCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.RejectExamCommand;
import com.englow3.exam.dto.command.SubmitExamForReviewCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.request.CreateExamRequest;
import com.englow3.exam.dto.request.RejectExamRequest;
import com.englow3.exam.dto.request.UpdateExamContentRequest;
import com.englow3.exam.dto.request.SearchExamRequest;
import com.englow3.exam.dto.request.UpdateExamRequest;
import com.englow3.exam.dto.response.ExamDetailResponse;
import com.englow3.exam.dto.response.ExamListItemResponse;
import com.englow3.exam.dto.response.ExamMediaResponse;
import com.englow3.exam.dto.response.ExamResponse;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.service.AdminExamService;
import com.englow3.exam.service.ExamContentService;
import com.englow3.exam.service.ExamReviewService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;

/**
 * Authoring and review.
 * <p>
 * The class gate admits staff as well as administrators, because writing papers is the staff role's whole job. The
 * three decisions that put a paper in front of learners - publish, approve, reject - carry their own {@code ADMIN}
 * gate. A method-level rule replaces the class-level one, so each of those is an explicit narrowing and not an accident
 * of ordering.
 */
@RestController
@RequestMapping("/api/admin/exams")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
class AdminExamController {

    private final AdminExamService adminExamService;
    private final ExamReviewService examReviewService;
    private final ExamContentService examContentService;

    AdminExamController(AdminExamService adminExamService, ExamReviewService examReviewService,
            ExamContentService examContentService) {
        this.adminExamService = adminExamService;
        this.examReviewService = examReviewService;
        this.examContentService = examContentService;
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

        return ResponseEntity.ok(ExamDetailResponse.from(result));
    }

    @PutMapping("/{id}")
    ResponseEntity<ExamResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateExamRequest request) {
        UpdateExamCommand command = new UpdateExamCommand(id, request.title(), request.description(),
                request.examType(), request.certificateType(), request.certificateVariant(), request.targetLevel(),
                request.durationSeconds(), request.maxRawScore(), request.passScore());

        return ResponseEntity.ok(ExamResponse.from(adminExamService.update(command)));
    }

    /** Staff hand a paper over for review. Available from a draft or from one that came back. */
    @PostMapping("/{id}/submit-for-review")
    ResponseEntity<ExamResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity
                .ok(ExamResponse.from(examReviewService.submitForReview(new SubmitExamForReviewCommand(id))));
    }

    /** Approving publishes in the same step - they are one decision, so there is no approved-but-unpublished state. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ExamResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(examReviewService.approve(new ApproveExamCommand(id))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ExamResponse> reject(@PathVariable UUID id, @Valid @RequestBody RejectExamRequest request) {
        return ResponseEntity
                .ok(ExamResponse.from(examReviewService.reject(new RejectExamCommand(id, request.note()))));
    }

    /**
     * Storage only - no row changes. The returned key is only real once it lands in a node of {@code PUT /content}'s
     * payload, which is why this never touches {@code AdminExamPaperQuery} or the exam at all.
     */
    @PostMapping(path = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ExamMediaResponse> uploadMedia(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ExamMediaResponse.from(examContentService.uploadMedia(id, file)));
    }

    /**
     * Whole-tree replace, draft only - also the wizard's autosave. Returns the tree the same way {@code detail} does.
     */
    @PutMapping("/{id}/content")
    ResponseEntity<ExamDetailResponse> replaceContent(@PathVariable UUID id,
            @Valid @RequestBody UpdateExamContentRequest request) {
        ExamDetailResult result = examContentService.replaceContent(UpdateExamContentCommand.of(id, request));

        return ResponseEntity.ok(ExamDetailResponse.from(result));
    }

    /** Publishing a draft outright, skipping review. An administrator holds the approval power either way. */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ExamResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(examReviewService.publish(new PublishExamCommand(id))));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ExamResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(examReviewService.archive(new ArchiveExamCommand(id))));
    }
}
