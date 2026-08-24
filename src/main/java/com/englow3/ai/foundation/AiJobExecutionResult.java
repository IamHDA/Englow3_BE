package com.englow3.ai.foundation;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

public record AiJobExecutionResult(JsonNode output, int inputTokens, int outputTokens, BigDecimal estimatedCost) {

    public AiJobExecutionResult(JsonNode output, int inputTokens, int outputTokens) {
        this(output, inputTokens, outputTokens, BigDecimal.ZERO);
    }
}
