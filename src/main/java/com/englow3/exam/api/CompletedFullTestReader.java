package com.englow3.exam.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A learner's completed IELTS full test sittings, for the roadmap's IELTS mock milestones. Plain values only; the exam
 * module's entities stay inside it.
 */
public interface CompletedFullTestReader {

    /** The learner's most recently completed sitting of {@code fullTestId}, completed at or after {@code since}. */
    Optional<CompletedSitting> latestCompleted(UUID fullTestId, UUID userId, Instant since);

    record CompletedSitting(UUID sittingId, UUID fullTestId, Instant completedAt, BigDecimal overallBand) {
    }
}
