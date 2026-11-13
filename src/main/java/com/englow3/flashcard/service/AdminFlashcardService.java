package com.englow3.flashcard.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.flashcard.dto.command.AddFlashcardsCommand;
import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;
import com.englow3.flashcard.dto.command.SaveAuthoringCommand;
import com.englow3.flashcard.dto.result.AuthoringResult;
import com.englow3.flashcard.dto.result.ContentReviewResult;
import com.englow3.flashcard.dto.result.FlashcardImportResult;
import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
import com.englow3.flashcard.entity.FlashcardSetStatus;

public interface AdminFlashcardService {
    AuthoringResult authoringDetail(UUID id);

    AuthoringResult saveAuthoring(SaveAuthoringCommand command);

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
