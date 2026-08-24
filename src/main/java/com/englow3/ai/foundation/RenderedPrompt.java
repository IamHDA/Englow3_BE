package com.englow3.ai.foundation;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RenderedPrompt(UUID versionId, String version, String systemPrompt, String userPrompt,
        JsonNode responseSchema) {
}
