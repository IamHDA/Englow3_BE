package com.englow3.quiz.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.command.SaveAuthoringCommand;
import com.englow3.quiz.dto.result.AuthoringResult;
import com.englow3.quiz.dto.result.ContentReviewResult;
import com.englow3.quiz.dto.result.QuizSummaryResult;
import com.englow3.quiz.entity.QuizStatus;

public interface AdminQuizService {
    AuthoringResult authoringDetail(UUID id);

    AuthoringResult saveAuthoring(SaveAuthoringCommand command);

    QuizSummaryResult create(CreateQuizCommand command);

    Page<ContentReviewResult> searchForAuthoring(QuizStatus status, String title, Pageable pageable);

    ContentReviewResult publish(UUID quizId);

    ContentReviewResult submitForReview(UUID quizId);

    ContentReviewResult approve(UUID quizId);

    ContentReviewResult reject(UUID quizId, String note);

    ContentReviewResult archive(UUID quizId);

    ContentReviewResult restore(UUID quizId);
}
