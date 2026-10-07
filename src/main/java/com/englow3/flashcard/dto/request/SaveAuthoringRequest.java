package com.englow3.flashcard.dto.request;

public record SaveAuthoringRequest(@jakarta.validation.constraints.PositiveOrZero Long version,
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateFlashcardSetRequest metadata,
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid AddFlashcardsRequest content) {
}
