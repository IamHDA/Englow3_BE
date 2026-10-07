package com.englow3.flashcard.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.flashcard.dto.command.*;
import com.englow3.flashcard.dto.result.*;
import com.englow3.flashcard.entity.FlashcardSetStatus;

public interface AdminFlashcardService {
    com.englow3.flashcard.dto.result.AuthoringResult authoringDetail(UUID id);

    com.englow3.flashcard.dto.result.AuthoringResult saveAuthoring(
            com.englow3.flashcard.dto.command.SaveAuthoringCommand command);

    FlashcardSetSummaryResult createSet(CreateFlashcardSetCommand command);

    FlashcardSetSummaryResult addCards(AddFlashcardsCommand command);

    FlashcardImportResult validateImport(String json);

    FlashcardImportResult importCards(UUID setId, String json);

    Page<ContentReviewResult> searchForAuthoring(FlashcardSetStatus status, String title, Pageable pageable);

    ContentReviewResult publish(UUID setId);

    ContentReviewResult submitForReview(UUID setId);

    ContentReviewResult approve(UUID setId);

    ContentReviewResult reject(UUID setId, String note);

    ContentReviewResult archive(UUID setId);

    ContentReviewResult restore(UUID setId);
}
