package com.englow3.learning.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.learning.dto.command.*;
import com.englow3.learning.dto.result.*;
import com.englow3.learning.entity.DictationLessonStatus;

public interface AdminDictationService {
    DictationLessonSummaryResult create(CreateDictationLessonCommand command);

    DictationLessonSummaryResult addSentences(AddDictationSentencesCommand command);

    DictationImportResult validateImport(String json);

    DictationImportResult importLessons(String json);

    Page<ContentReviewResult> searchForAuthoring(DictationLessonStatus status, String title, Pageable pageable);

    ContentReviewResult publish(UUID lessonId);

    ContentReviewResult submitForReview(UUID lessonId);

    ContentReviewResult approve(UUID lessonId);

    ContentReviewResult reject(UUID lessonId, String note);

    ContentReviewResult archive(UUID lessonId);
}
