package com.englow3.quiz.dto.request;

public record SaveAuthoringRequest(@jakarta.validation.constraints.PositiveOrZero Long version,
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateQuizRequest metadata,
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid AddQuizQuestionsRequest content) {
}
