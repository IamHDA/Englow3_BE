package com.englow3.user.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface PlacementRecorder {
    void record(UUID userId, UUID attemptId, BigDecimal scorePercentage);
}
