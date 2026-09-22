package com.englow3.learning.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.learning.dto.command.AddDictationSentencesCommand;
import com.englow3.learning.dto.command.CreateDictationLessonCommand;
import com.englow3.learning.dto.request.AddDictationSentencesRequest;
import com.englow3.learning.dto.request.CreateDictationLessonRequest;
import com.englow3.learning.dto.request.RejectContentRequest;
import com.englow3.learning.dto.response.ContentReviewResponse;
import com.englow3.learning.dto.response.DictationLessonResponse;
import com.englow3.learning.service.AdminDictationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dictation")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminDictationController {

    private final AdminDictationService adminDictationService;

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
