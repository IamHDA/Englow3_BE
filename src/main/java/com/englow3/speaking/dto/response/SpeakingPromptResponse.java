package com.englow3.speaking.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingPromptResult;
import com.fasterxml.jackson.annotation.JsonRawValue;

public record SpeakingPromptResponse(UUID id, String slug, String title, String category, String targetLevel,
        String referenceText, String ipaTranscript, String translationVi, String phonemeTarget,
        /** Written straight into the response as JSON rather than re-encoded as a string of JSON. */
        @JsonRawValue String tips, BigDecimal bestScorePercent) {

    public static SpeakingPromptResponse from(SpeakingPromptResult result) {
        return new SpeakingPromptResponse(result.id(), result.slug(), result.title(), result.category(),
                result.targetLevel(), result.referenceText(), result.ipaTranscript(), result.translationVi(),
                result.phonemeTarget(), result.tipsJson(), result.bestScorePercent());
    }
}
