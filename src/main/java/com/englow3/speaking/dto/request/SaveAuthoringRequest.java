package com.englow3.speaking.dto.request;

public record SaveAuthoringRequest(@jakarta.validation.constraints.PositiveOrZero Long version,
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateSpeakingPromptRequest metadata) {
}
