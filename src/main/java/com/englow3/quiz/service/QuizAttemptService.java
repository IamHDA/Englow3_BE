package com.englow3.quiz.service;

import java.util.UUID;

import com.englow3.quiz.dto.command.SubmitQuizAttemptCommand;
import com.englow3.quiz.dto.result.QuizAttemptResult;
import com.englow3.quiz.dto.result.QuizPaperResult;

public interface QuizAttemptService {
    QuizAttemptResult start(UUID quizId);

    QuizPaperResult paperForAttempt(UUID attemptId);

    QuizAttemptResult submit(SubmitQuizAttemptCommand command);

    QuizAttemptResult result(UUID attemptId);
}
