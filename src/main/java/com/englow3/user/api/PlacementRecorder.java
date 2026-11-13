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

    /**
     * An IELTS band measured by the exam module's full test, the IELTS learner's placement test. Recorded first with
     * the provisional band and again with the overall one; either moves onboarding on from the level step the way a
     * placement does, and neither sends a finished learner back through it.
     *
     * @param examAttemptId
     *            the full test's Listening + Reading sitting, kept as the placement it came from
     * @param level
     *            CEFR band aligned with {@code band}
     */
    void recordIeltsBand(UUID userId, UUID examAttemptId, String level, BigDecimal band);
}
