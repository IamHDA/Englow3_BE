package com.englow3.speaking.dto.result;

import java.math.BigDecimal;
import java.util.UUID;

import com.englow3.speaking.entity.SpeakingPrompt;

/**
 * A prompt as a learner sees it.
 *
 * @param bestScorePercent
 *            this learner's own best, null until they have finished one. Per learner rather than per prompt, which is
 *            why it is carried here instead of on the entity.
 */
public record SpeakingPromptResult(UUID id, String slug, String title, String category, String targetLevel,
        String referenceText, String ipaTranscript, String translationVi, String phonemeTarget, String tipsJson,
        BigDecimal bestScorePercent) {

    public static SpeakingPromptResult of(SpeakingPrompt prompt, BigDecimal bestScorePercent) {
        return new SpeakingPromptResult(prompt.getId(), prompt.getSlug(), prompt.getTitle(), prompt.getCategory(),
                prompt.getTargetLevel(), prompt.getReferenceText(), prompt.getIpaTranscript(),
                prompt.getTranslationVi(), prompt.getPhonemeTarget(), prompt.getTips(), bestScorePercent);
    }
}
