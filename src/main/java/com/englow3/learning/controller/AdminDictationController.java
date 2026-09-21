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
import com.englow3.learning.dto.response.DictationLessonResponse;
import com.englow3.learning.service.AdminDictationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dictation")
@PreAuthorize("hasRole('ADMIN')")
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

    @PostMapping("/lessons/{id}/publish")
    ResponseEntity<DictationLessonResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(DictationLessonResponse.from(adminDictationService.publish(id)));
    }

    @PostMapping("/lessons/{id}/archive")
    ResponseEntity<DictationLessonResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(DictationLessonResponse.from(adminDictationService.archive(id)));
    }
}
