package com.englow3.ai.foundation;

public record ResolvedAiPolicy(String provider, String model, double temperature, int maxOutputTokens,
        boolean enabled) {
}
