package com.englow3.learning.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddDictationSentencesRequest(@NotEmpty @Size(max = 200) List<@NotNull @Valid SentenceRequest> sentences) {

    public record SentenceRequest(@NotBlank @Size(max = 1000) String text, @Size(max = 1000) String translationVi,
            @NotBlank @Size(max = 500) String audioObjectKey, @Positive int audioDurationSeconds,
            @Size(max = 200) String hintFirstLetters, @Size(max = 100) String hintRevealWord,
            @Size(max = 1000) String hintPartialTranscript) {
    }
}
