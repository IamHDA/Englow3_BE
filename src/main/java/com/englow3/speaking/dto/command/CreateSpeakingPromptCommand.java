package com.englow3.speaking.dto.command;

import java.util.List;

public record CreateSpeakingPromptCommand(String slug, String title, String category, String targetLevel,
        String referenceText, String ipaTranscript, String translationVi, String phonemeTarget, List<String> tips) {
}
