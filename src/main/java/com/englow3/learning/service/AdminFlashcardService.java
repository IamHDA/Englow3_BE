package com.englow3.learning.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.learning.dto.command.*;
import com.englow3.learning.dto.result.*;
import com.englow3.learning.entity.FlashcardSetStatus;

public interface AdminFlashcardService {
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
}
