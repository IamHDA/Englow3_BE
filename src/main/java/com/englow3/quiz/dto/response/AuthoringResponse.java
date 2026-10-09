package com.englow3.quiz.dto.response;

import java.util.List;
import java.util.UUID;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote, CreateQuizCommand metadata,
        List<AddQuizQuestionsCommand.NewQuestion> questions) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(), r.questions());
    }
}
