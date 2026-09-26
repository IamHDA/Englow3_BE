package com.englow3.quiz.dto.command;

import java.util.List;
import java.util.UUID;

public record SubmitQuizAttemptCommand(UUID attemptId, List<SubmittedAnswer> answers) {

    /** The response is whatever the question's type means by an answer - see QuizGrader. */
    public record SubmittedAnswer(UUID questionId, String response) {
    }
}
