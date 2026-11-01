package com.englow3.speaking.dto.command;

import java.util.List;

import com.englow3.speaking.entity.SpeakingPromptCategory;

public record CreateSpeakingPromptCommand(String slug, String title, SpeakingPromptCategory category,
        String targetLevel, String referenceText, String ipaTranscript, String translationVi, String phonemeTarget,
        List<String> tips) {
}
