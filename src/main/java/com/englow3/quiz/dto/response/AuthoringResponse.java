package com.englow3.quiz.dto.response;

import java.util.UUID;
import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote, CreateQuizCommand metadata,
        java.util.List<com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion> questions) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(), r.questions());
    }
}
