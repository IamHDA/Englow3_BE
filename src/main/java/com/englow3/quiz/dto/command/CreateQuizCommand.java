package com.englow3.quiz.dto.command;

public record CreateQuizCommand(String slug, String title, String description, String category, String targetLevel,
        int timeLimitSeconds, short passingScorePercent) {
}
