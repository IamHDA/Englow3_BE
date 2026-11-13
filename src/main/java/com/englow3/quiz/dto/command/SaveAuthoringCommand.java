package com.englow3.quiz.dto.command;

import java.util.List;
import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateQuizCommand metadata,
        List<AddQuizQuestionsCommand.NewQuestion> questions) {
}
