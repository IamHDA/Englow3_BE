package com.englow3.quiz.dto.result;

import java.util.List;
import java.util.UUID;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.command.CreateQuizCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote, CreateQuizCommand metadata,
        List<AddQuizQuestionsCommand.NewQuestion> questions) {
}
