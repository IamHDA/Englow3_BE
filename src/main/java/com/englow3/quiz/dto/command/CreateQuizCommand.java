package com.englow3.quiz.dto.command;

import com.englow3.quiz.entity.QuizCategory;

public record CreateQuizCommand(String slug, String title, String description, QuizCategory category,
        String targetLevel, int timeLimitSeconds, short passingScorePercent) {
}
