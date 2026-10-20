package com.englow3.tutor.dto.result;

import java.util.List;

public record TutorPolicyResult(boolean accepting, List<String> allowedModes, long version, boolean environmentEnabled,
        String model, int dailyLimit, boolean streamingEnabled, boolean voiceEnabled) {
}
