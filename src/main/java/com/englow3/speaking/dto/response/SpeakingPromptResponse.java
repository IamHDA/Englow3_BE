package com.englow3.speaking.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingPromptResult;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;

public record SpeakingPromptResponse(UUID id, String slug, String title, String category,
        @Schema(nullable = true) String targetLevel, String referenceText,
        @Schema(nullable = true) String ipaTranscript, @Schema(nullable = true) String translationVi,
        @Schema(nullable = true) String phonemeTarget,
        /** Written straight into the response as JSON rather than re-encoded as a string of JSON. */
        @JsonRawValue String tips, @Schema(nullable = true) BigDecimal bestScorePercent) {

    public static SpeakingPromptResponse from(SpeakingPromptResult result) {
        return new SpeakingPromptResponse(result.id(), result.slug(), result.title(), result.category(),
                result.targetLevel(), result.referenceText(), result.ipaTranscript(), result.translationVi(),
                result.phonemeTarget(), result.tipsJson(), result.bestScorePercent());
    }
}
