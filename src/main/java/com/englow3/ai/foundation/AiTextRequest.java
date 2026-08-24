package com.englow3.ai.foundation;

public record AiTextRequest(String model, String systemPrompt, String userPrompt, double temperature,
        int maxOutputTokens, boolean jsonOutput) {
}
