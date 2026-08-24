package com.englow3.ai.foundation;

import java.math.BigDecimal;

public record ResolvedAiPolicy(String provider, String model, double temperature, int maxOutputTokens, boolean enabled,
        BigDecimal inputCostPerMillion, BigDecimal outputCostPerMillion) {
}
