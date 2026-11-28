package com.englow3.flashcard.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.flashcard.dto.command.FlashcardSetScope;
import com.englow3.flashcard.dto.command.RateFlashcardCommand;
import com.englow3.flashcard.dto.result.FlashcardResult;
import com.englow3.flashcard.dto.result.FlashcardReviewResult;
import com.englow3.flashcard.dto.result.FlashcardSetDetailResult;
import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;

public interface FlashcardService {
    /** One shelf of sets the caller may open: the catalogue, their own, or what other learners let them see. */
    Page<FlashcardSetSummaryResult> searchSets(FlashcardSetScope scope, String topic, String title, String sort,
            Pageable pageable);

    FlashcardSetDetailResult setDetail(UUID setId, Pageable pageable);

    FlashcardSetSummaryResult setSummary(UUID setId);

    com.englow3.flashcard.dto.result.FlashcardCatalogueInfoResult catalogueInfo();

    List<FlashcardResult> studyQueue(UUID setId, int limit);

    FlashcardReviewResult rate(RateFlashcardCommand command);
}
