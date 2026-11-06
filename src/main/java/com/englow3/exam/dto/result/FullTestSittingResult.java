package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.FullTestPartGrading;
import com.englow3.exam.entity.FullTestSitting;
import com.englow3.exam.entity.FullTestSittingStatus;

/**
 * A learner's sitting with everything the screen needs to send them to each part: the paper to sit and the five tasks
 * to answer, and which of them are already in.
 *
 * @param gradesDueAt
 *            grading still missing by then is given up on and the sitting expires
 * @param provisionalBand
 *            the lower of Listening and Reading, shown as an estimate until the overall band exists
 */
public record FullTestSittingResult(UUID id, UUID fullTestId, String title, FullTestSittingStatus status,
        Instant startedAt, Instant deadlineAt, Instant gradesDueAt, Instant completedAt, UUID examId, UUID examAttemptId,
        java.util.List<PartResult> parts, BigDecimal listeningBand, BigDecimal readingBand, BigDecimal writingBand,
        BigDecimal speakingBand, BigDecimal provisionalBand, BigDecimal overallBand) {

    /**
     * One Writing or Speaking slot: the task to answer, the answer attached, if any, and how its grading stands -
     * null when nothing is attached or when the caller did not look (the list of sittings).
     */
    public record PartResult(FullTest.Slot slot, UUID taskId, UUID attemptId, FullTestPartGrading grading) {
    }

    /** Without grading states, for lists. */
    public static FullTestSittingResult of(FullTestSitting sitting, FullTest test) {
        return of(sitting, test, java.util.Collections.nCopies(FullTest.Slot.values().length, null));
    }

    /**
     * @param grading
     *            per slot in {@link FullTest.Slot} order, null entries allowed
     */
    public static FullTestSittingResult of(FullTestSitting sitting, FullTest test,
            java.util.List<FullTestPartGrading> grading) {
        java.util.List<PartResult> parts = new java.util.ArrayList<>();
        for (FullTest.Slot slot : FullTest.Slot.values()) {
            parts.add(new PartResult(slot, test.taskId(slot), sitting.partAttemptIds().get(slot.ordinal()),
                    grading.get(slot.ordinal())));
        }
        return new FullTestSittingResult(sitting.getId(), test.getId(), test.getTitle(), sitting.getStatus(),
                sitting.getStartedAt(), sitting.getDeadlineAt(), sitting.gradesDueAt(), sitting.getCompletedAt(),
                test.getExamId(),
                sitting.getExamAttemptId(), parts, sitting.getListeningBand(), sitting.getReadingBand(),
                sitting.getWritingBand(), sitting.getSpeakingBand(), sitting.getProvisionalBand(),
                sitting.getOverallBand());
    }
}
