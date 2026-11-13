package com.englow3.tutor.dto.result;

import java.time.Instant;

public record TutorUsageResult(boolean enabled, int limit, int used, int remaining, Instant resetsAt,
        int maxMessageLength, boolean streaming, boolean voice, java.util.List<String> allowedModes,
        java.util.List<String> supportedContexts, boolean memorySupported) {
}
