package com.englow3.quiz.api;

import java.util.UUID;

/** A submitted, scored question belonging to the caller. Never exposes an active paper's key. */
public interface SubmittedQuizContextReader {
    Question read(UUID attemptId, UUID questionId);

    record Question(UUID attemptId, UUID questionId, String quizTitle, String prompt, String learnerAnswer,
            String correctAnswer, String explanation, boolean correct) {
    }
}
