package com.englow3.learning.controller;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.learning.dto.command.SubmitDictationCommand;
import com.englow3.learning.dto.request.SubmitDictationRequest;
import com.englow3.learning.dto.response.DictationLessonResponse;
import com.englow3.learning.dto.response.DictationSentenceResponse;
import com.englow3.learning.dto.response.DictationSubmissionResponse;
import com.englow3.learning.dto.response.FlashcardMediaUrls;
import com.englow3.learning.service.DictationService;
import com.englow3.shared.page.PageResponse;
import com.englow3.shared.storage.ObjectStorageClient;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dictation")
class DictationController {

    private final DictationService dictationService;
    private final FlashcardMediaUrls mediaUrls;

    DictationController(DictationService dictationService, ObjectStorageClient objectStorage,
            @Value("${app.storage.learning-bucket}") String learningBucket,
            @Value("${app.storage.learning-media-url-ttl:PT3H}") Duration mediaUrlTtl) {
        this.dictationService = dictationService;
        this.mediaUrls = new FlashcardMediaUrls(objectStorage, learningBucket, mediaUrlTtl);
    }

    @GetMapping("/lessons")
    ResponseEntity<PageResponse<DictationLessonResponse>> lessons(@RequestParam(required = false) String topic,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(dictationService.searchPublished(topic, title, pageable).map(DictationLessonResponse::from)));
    }

    @GetMapping("/lessons/{id}")
    ResponseEntity<DictationLessonDetailResponse> lessonDetail(@PathVariable UUID id) {
        var detail = dictationService.lessonDetail(id);
        return ResponseEntity.ok(new DictationLessonDetailResponse(DictationLessonResponse.from(detail.lesson()), detail
                .sentences().stream().map(sentence -> DictationSentenceResponse.from(sentence, mediaUrls)).toList()));
    }

    /** The only endpoint that returns a transcript, and only in exchange for an answer. */
    @PostMapping("/sentences/{id}/attempts")
    ResponseEntity<DictationSubmissionResponse> submit(@PathVariable UUID id,
            @Valid @RequestBody SubmitDictationRequest request) {
        return ResponseEntity.ok(DictationSubmissionResponse
                .from(dictationService.submit(new SubmitDictationCommand(id, request.response()))));
    }

    record DictationLessonDetailResponse(DictationLessonResponse lesson, List<DictationSentenceResponse> sentences) {
    }
}
