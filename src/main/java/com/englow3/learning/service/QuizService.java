package com.englow3.learning.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.learning.dto.command.SubmitQuizAttemptCommand;
import com.englow3.learning.dto.result.*;

public interface QuizService {
    Page<QuizSummaryResult> searchPublished(String category, String title, Pageable pageable);

    QuizAttemptResult start(UUID quizId);

    QuizPaperResult paperForAttempt(UUID attemptId);

    QuizAttemptResult submit(SubmitQuizAttemptCommand command);

    QuizAttemptResult result(UUID attemptId);
}
