package com.englow3.quiz.dto.command;

import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateQuizCommand metadata,
        java.util.List<AddQuizQuestionsCommand.NewQuestion> questions) {
}
