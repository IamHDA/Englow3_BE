package com.englow3.speaking.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSpeakingPromptRequest(@NotBlank @Size(max = 120) String slug,
        @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 60) String category,
        @Size(max = 2) String targetLevel,
        /** What the learner is asked to say. Not optional - an accuracy score is accuracy against something. */
        @NotBlank @Size(max = 2000) String referenceText, @Size(max = 2000) String ipaTranscript,
        @Size(max = 2000) String translationVi, @Size(max = 100) String phonemeTarget, List<@NotBlank String> tips) {
}
