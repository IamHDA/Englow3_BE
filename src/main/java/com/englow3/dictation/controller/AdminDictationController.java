package com.englow3.dictation.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.dictation.entity.DictationLessonStatus;
import com.englow3.dictation.dto.command.AddDictationSentencesCommand;
import com.englow3.dictation.dto.command.CreateDictationLessonCommand;
import com.englow3.dictation.dto.request.AddDictationSentencesRequest;
import com.englow3.dictation.dto.request.CreateDictationLessonRequest;
import com.englow3.dictation.dto.request.RejectContentRequest;
import com.englow3.dictation.dto.response.ContentReviewResponse;
import com.englow3.dictation.dto.response.DictationLessonResponse;
import com.englow3.shared.page.PageResponse;
import com.englow3.dictation.dto.result.DictationImportResult;
import com.englow3.shared.error.BadRequestException;
import com.englow3.dictation.service.AdminDictationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dictation")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminDictationController {

    private final AdminDictationService adminDictationService;

    /**
     * The authoring list. Separate from the learner catalogue because that one shows published lessons only - an
     * administrator with no way to see a draft has no way to review one. A null status means every status, so the same
     * endpoint serves the full list and the review queue.
     */
    @GetMapping("/lessons")
    ResponseEntity<PageResponse<ContentReviewResponse>> search(
            @RequestParam(required = false) DictationLessonStatus status, @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                adminDictationService.searchForAuthoring(status, title, pageable).map(ContentReviewResponse::from)));
    }

    /** Says what a generated shadowing batch would do, and writes nothing. */
    @PostMapping(path = "/import/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DictationImportResult> validateImport(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(adminDictationService.validateImport(readUpload(file)));
    }

    /** One draft lesson per clip. A clip already imported is skipped, so re-running a batch is safe. */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DictationImportResult> importLessons(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(adminDictationService.importLessons(readUpload(file)));
    }

    private static String readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("DICTATION_IMPORT_EMPTY", "No file was uploaded");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new BadRequestException("DICTATION_IMPORT_EMPTY", "The uploaded file could not be read");
        }
    }

    @PostMapping("/lessons")
    ResponseEntity<DictationLessonResponse> create(@Valid @RequestBody CreateDictationLessonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DictationLessonResponse
                        .from(adminDictationService.create(new CreateDictationLessonCommand(request.slug(),
                                request.title(), request.topic(), request.targetLevel()))));
    }

    @PostMapping("/lessons/{id}/sentences")
    ResponseEntity<DictationLessonResponse> addSentences(@PathVariable UUID id,
            @Valid @RequestBody AddDictationSentencesRequest request) {
        var sentences = request.sentences().stream()
                .map(sentence -> new AddDictationSentencesCommand.NewSentence(sentence.text(), sentence.translationVi(),
                        sentence.audioObjectKey(), sentence.audioDurationSeconds(), sentence.hintFirstLetters(),
                        sentence.hintRevealWord(), sentence.hintPartialTranscript()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(DictationLessonResponse
                .from(adminDictationService.addSentences(new AddDictationSentencesCommand(id, sentences))));
    }

    /** Staff hand a lesson over for review. Available from a draft or from one that came back. */
    @PostMapping("/lessons/{id}/submit-for-review")
    ResponseEntity<ContentReviewResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminDictationService.submitForReview(id)));
    }

    @PostMapping("/lessons/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminDictationService.approve(id)));
    }

    @PostMapping("/lessons/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectContentRequest request) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminDictationService.reject(id, request.note())));
    }

    @PostMapping("/lessons/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminDictationService.publish(id)));
    }

    @PostMapping("/lessons/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminDictationService.archive(id)));
    }
}
