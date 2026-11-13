package com.englow3.exam.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A learner's sitting as section scores, for the learning roadmap: its milestones and its level check both need to know
 * how a sitting went without reading the exam module's tables.
 */
public interface ScoredExamAttemptReader {

    /** Empty when the attempt does not exist or belongs to someone else. */
    Optional<Sitting> read(UUID attemptId, UUID ownerId);

    /**
     * The learner's most recent scored full sitting of {@code examId} started at or after {@code since} - how the
     * roadmap finds the sitting a learner made of a level-check paper without the exam screens having to report it.
     */
    Optional<Sitting> latestFullSitting(UUID examId, UUID ownerId, Instant since);

    /**
     * @param examType
     *            PLACEMENT, MOCK or LEVEL_CHECK
     * @param scored
     *            false while the sitting is still open; sections and scores are then empty or null
     * @param full
     *            a full sitting rather than a practice of chosen parts
     * @param convertedScore
     *            the certificate-scale score (band, TOEIC points) where the paper has a conversion; otherwise null
     */
    record Sitting(UUID attemptId, UUID examId, String examType, String certificateType, String certificateVariant,
            boolean scored, boolean full, BigDecimal scorePercentage, BigDecimal convertedScore, Instant scoredAt,
            List<SectionScore> sections) {
    }

    /** Points awarded in one section against the points it was worth; unanswered questions count as zero. */
    record SectionScore(String sectionType, BigDecimal awarded, BigDecimal max) {
    }
}
