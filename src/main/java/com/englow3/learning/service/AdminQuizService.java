package com.englow3.learning.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.learning.dto.command.*;
import com.englow3.learning.dto.result.*;
import com.englow3.learning.entity.QuizStatus;

public interface AdminQuizService {
    QuizSummaryResult create(CreateQuizCommand command);

    QuizSummaryResult addQuestions(AddQuizQuestionsCommand command);

    Page<ContentReviewResult> searchForAuthoring(QuizStatus status, String title, Pageable pageable);

    ContentReviewResult publish(UUID quizId);

    ContentReviewResult submitForReview(UUID quizId);

    ContentReviewResult approve(UUID quizId);

    ContentReviewResult reject(UUID quizId, String note);

    ContentReviewResult archive(UUID quizId);
}
