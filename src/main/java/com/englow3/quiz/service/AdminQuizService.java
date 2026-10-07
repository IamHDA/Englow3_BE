package com.englow3.quiz.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.quiz.dto.command.*;
import com.englow3.quiz.dto.result.*;
import com.englow3.quiz.entity.QuizStatus;

public interface AdminQuizService {
    com.englow3.quiz.dto.result.AuthoringResult authoringDetail(UUID id);

    com.englow3.quiz.dto.result.AuthoringResult saveAuthoring(
            com.englow3.quiz.dto.command.SaveAuthoringCommand command);

    QuizSummaryResult create(CreateQuizCommand command);

    Page<ContentReviewResult> searchForAuthoring(QuizStatus status, String title, Pageable pageable);

    ContentReviewResult publish(UUID quizId);

    ContentReviewResult submitForReview(UUID quizId);

    ContentReviewResult approve(UUID quizId);

    ContentReviewResult reject(UUID quizId, String note);

    ContentReviewResult archive(UUID quizId);

    ContentReviewResult restore(UUID quizId);
}
