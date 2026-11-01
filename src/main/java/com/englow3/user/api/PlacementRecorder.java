package com.englow3.user.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface PlacementRecorder {
    void record(UUID userId, UUID attemptId, BigDecimal scorePercentage);

    /**
     * A level measured by the roadmap's level check (vocabulary, listening and speaking) rather than the placement
     * paper. {@code level} is a CEFR band; an unknown value is refused. Moves onboarding on from the level step the
     * same way a placement does.
     */
    void recordLevelCheck(UUID userId, UUID levelCheckId, String level);
}
