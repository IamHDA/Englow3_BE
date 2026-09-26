package com.englow3.learning.controller;

import java.util.List;
import java.util.UUID;

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
import com.englow3.learning.dto.response.MistakeSentenceResponse;
import com.englow3.learning.dto.response.DictationSentenceResponse;
import com.englow3.learning.dto.response.DictationStatsResponse;
import com.englow3.learning.dto.response.DictationSubmissionResponse;
import com.englow3.learning.service.DictationService;
import com.englow3.learning.service.DictationStatsService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dictation")
class DictationController {

    /** Windows the statistics screen offers. A request outside them is clamped rather than refused. */
    private static final int MIN_PERIOD_DAYS = 1;
    private static final int MAX_PERIOD_DAYS = 365;
    private static final int DEFAULT_PERIOD_DAYS = 7;

    private final DictationService dictationService;
    private final DictationStatsService dictationStatsService;

    DictationController(DictationService dictationService, DictationStatsService dictationStatsService) {
        this.dictationService = dictationService;
        this.dictationStatsService = dictationStatsService;
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
        return ResponseEntity.ok(new DictationLessonDetailResponse(DictationLessonResponse.from(detail.lesson()),
                detail.sentences().stream().map(DictationSentenceResponse::from).toList()));
    }

    @GetMapping("/stats")
    ResponseEntity<DictationStatsResponse> stats(
            @RequestParam(defaultValue = "" + DEFAULT_PERIOD_DAYS) int periodDays) {
        int clamped = Math.min(Math.max(periodDays, MIN_PERIOD_DAYS), MAX_PERIOD_DAYS);
        return ResponseEntity.ok(DictationStatsResponse.from(dictationStatsService.statsFor(clamped)));
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

    /**
     * Lines this learner keeps getting wrong, with the audio to practise them again. Across every lesson: "what do I
     * keep getting wrong" is a question about the learner, not about a lesson.
     */
    @GetMapping("/mistakes")
    public ResponseEntity<List<MistakeSentenceResponse>> mistakes() {
        return ResponseEntity.ok(
                dictationStatsService.mistakeQueue().sentences().stream().map(MistakeSentenceResponse::from).toList());
    }
}
