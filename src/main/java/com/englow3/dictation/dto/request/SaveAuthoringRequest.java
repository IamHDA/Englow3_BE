package com.englow3.dictation.dto.request;

import java.util.List;

public record SaveAuthoringRequest(@jakarta.validation.constraints.PositiveOrZero Long version,
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid CreateDictationLessonRequest metadata,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 200) List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid SentenceRequest> sentences) {
    public record SentenceRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000) String text,
            @jakarta.validation.constraints.Size(max = 1000) String translationVi,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String audioObjectKey,
            @jakarta.validation.constraints.Positive int audioDurationSeconds, String hintFirstLetters,
            String hintRevealWord, String hintPartialTranscript, Integer audioStartMs, Integer audioEndMs) {
    }
}
