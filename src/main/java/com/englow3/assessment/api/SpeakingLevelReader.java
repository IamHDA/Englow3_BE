package com.englow3.assessment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A learner's graded spoken answer as a CEFR band, for the learning roadmap's level check.
 * <p>
 * Assessment owns both the grade and how a grade maps to a level, so the roadmap asks here rather than reading the
 * report or the conversion table itself.
 */
public interface SpeakingLevelReader {

    /**
     * Empty when the attempt does not exist, belongs to someone else, or is not a speaking attempt. A speaking attempt
     * that is not graded yet comes back with {@code completed = false} and no level.
     */
    Optional<SpeakingLevel> read(UUID attemptId, UUID ownerId);

    /**
     * @param taskId
     *            the task the answer was to, so a caller can tell an answer to the task it asked for from any other
     * @param overall
     *            mean of the four criteria on the 0-9 scale, in half bands; null until graded
     * @param level
     *            CEFR band from the conversion in force when it was graded; null until graded
     * @param failed
     *            grading will not happen without the learner retrying or asking for a human review
     */
    record SpeakingLevel(UUID attemptId, UUID taskId, boolean completed, boolean failed, BigDecimal overall,
            String level, Instant gradedAt) {
    }
}
