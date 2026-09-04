package com.englow3.ai.foundation;

import java.math.BigDecimal;

public record AiTextResult(String content, String model, int inputTokens, int outputTokens, BigDecimal estimatedCost) {

    public AiTextResult(String content, String model, int inputTokens, int outputTokens) {
        this(content, model, inputTokens, outputTokens, BigDecimal.ZERO);
    }
}
