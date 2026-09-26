package com.englow3.flashcard.controller;

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

import com.englow3.flashcard.dto.command.RateFlashcardCommand;
import com.englow3.flashcard.dto.request.RateFlashcardRequest;
import com.englow3.flashcard.dto.response.FlashcardResponse;
import com.englow3.flashcard.dto.response.FlashcardReviewResponse;
import com.englow3.flashcard.dto.response.FlashcardSetResponse;
import com.englow3.flashcard.dto.response.FlashcardStatsResponse;
import com.englow3.flashcard.service.FlashcardService;
import com.englow3.flashcard.service.FlashcardStatsService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {

    /**
     * How many cards one sitting hands out. Large enough for a real session, small enough that a learner opening a
     * 500-card set is not handed all of it at once.
     */
    private static final int DEFAULT_STUDY_LIMIT = 20;
    private static final int MAX_STUDY_LIMIT = 100;

    /** Windows the statistics screen offers. A request outside them is clamped rather than refused. */
    private static final int MIN_PERIOD_DAYS = 1;
    private static final int MAX_PERIOD_DAYS = 365;
    private static final int DEFAULT_PERIOD_DAYS = 7;

    private final FlashcardService flashcardService;
    private final FlashcardStatsService flashcardStatsService;

    public FlashcardController(FlashcardService flashcardService, FlashcardStatsService flashcardStatsService) {
        this.flashcardService = flashcardService;
        this.flashcardStatsService = flashcardStatsService;
    }

    @GetMapping("/sets")
    public ResponseEntity<PageResponse<FlashcardSetResponse>> sets(@RequestParam(required = false) String topic,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(flashcardService.searchPublishedSets(topic, title, pageable).map(FlashcardSetResponse::from)));
    }

    @GetMapping("/sets/{id}")
    public ResponseEntity<FlashcardSetDetailResponse> setDetail(@PathVariable UUID id) {
        var detail = flashcardService.setDetail(id);
        return ResponseEntity.ok(new FlashcardSetDetailResponse(FlashcardSetResponse.from(detail.set()),
                detail.cards().stream().map(FlashcardResponse::from).toList()));
    }

    /** What to study now: due cards first, then unseen ones to fill the session. */
    @GetMapping("/sets/{id}/study-queue")
    public ResponseEntity<List<FlashcardResponse>> studyQueue(@PathVariable UUID id,
            @RequestParam(defaultValue = "" + DEFAULT_STUDY_LIMIT) int limit) {
        return ResponseEntity.ok(flashcardService.studyQueue(id, Math.min(Math.max(limit, 1), MAX_STUDY_LIMIT)).stream()
                .map(FlashcardResponse::from).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<FlashcardStatsResponse> stats(
            @RequestParam(defaultValue = "" + DEFAULT_PERIOD_DAYS) int periodDays) {
        int clamped = Math.min(Math.max(periodDays, MIN_PERIOD_DAYS), MAX_PERIOD_DAYS);
        return ResponseEntity.ok(FlashcardStatsResponse.from(flashcardStatsService.statsFor(clamped)));
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<FlashcardReviewResponse> rate(@PathVariable UUID id,
            @Valid @RequestBody RateFlashcardRequest request) {
        return ResponseEntity.ok(FlashcardReviewResponse.from(
                flashcardService.rate(new RateFlashcardCommand(id, request.rating(), request.timeSpentSeconds()))));
    }

    public record FlashcardSetDetailResponse(FlashcardSetResponse set, List<FlashcardResponse> cards) {
    }
}
