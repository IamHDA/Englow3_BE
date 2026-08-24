package com.englow3.ai.foundation;

import com.fasterxml.jackson.databind.JsonNode;

public record AiJobExecutionResult(JsonNode output, int inputTokens, int outputTokens) {
}
