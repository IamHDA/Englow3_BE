package com.englow3.quiz.dto.command;

import com.englow3.quiz.entity.QuizCategory;

public record CreateQuizCommand(String slug, String title, String description, QuizCategory category,
        String targetLevel, int timeLimitSeconds, short passingScorePercent, String certificateType) {

    /** General practice, tied to no certificate. */
    public CreateQuizCommand(String slug, String title, String description, QuizCategory category, String targetLevel,
            int timeLimitSeconds, short passingScorePercent) {
        this(slug, title, description, category, targetLevel, timeLimitSeconds, passingScorePercent, null);
    }
}
