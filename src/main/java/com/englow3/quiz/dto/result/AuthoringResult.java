package com.englow3.quiz.dto.result;

import java.util.UUID;
import com.englow3.quiz.dto.command.CreateQuizCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote, CreateQuizCommand metadata,
        java.util.List<com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion> questions) {
}
