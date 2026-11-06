package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.FullTestSitting;
import com.englow3.exam.entity.FullTestSittingStatus;

/**
 * A learner's sitting with everything the screen needs to send them to each part: the paper to sit and the five tasks
 * to answer, and which of them are already in.
 *
 * @param provisionalBand
 *            the lower of Listening and Reading, shown as an estimate until the overall band exists
 */
public record FullTestSittingResult(UUID id, UUID fullTestId, String title, FullTestSittingStatus status,
        Instant startedAt, Instant deadlineAt, Instant completedAt, UUID examId, UUID examAttemptId,
        java.util.List<PartResult> parts, BigDecimal listeningBand, BigDecimal readingBand, BigDecimal writingBand,
        BigDecimal speakingBand, BigDecimal provisionalBand, BigDecimal overallBand) {

    /** One Writing or Speaking slot: the task to answer and the answer attached, if any. */
    public record PartResult(FullTest.Slot slot, UUID taskId, UUID attemptId) {
    }

    public static FullTestSittingResult of(FullTestSitting sitting, FullTest test) {
        java.util.List<PartResult> parts = new java.util.ArrayList<>();
        for (FullTest.Slot slot : FullTest.Slot.values()) {
            parts.add(new PartResult(slot, test.taskId(slot), sitting.partAttemptIds().get(slot.ordinal())));
        }
        return new FullTestSittingResult(sitting.getId(), test.getId(), test.getTitle(), sitting.getStatus(),
                sitting.getStartedAt(), sitting.getDeadlineAt(), sitting.getCompletedAt(), test.getExamId(),
                sitting.getExamAttemptId(), parts, sitting.getListeningBand(), sitting.getReadingBand(),
                sitting.getWritingBand(), sitting.getSpeakingBand(), sitting.getProvisionalBand(),
                sitting.getOverallBand());
    }
}
