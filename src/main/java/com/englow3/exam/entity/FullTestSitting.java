package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.englow3.exam.helper.IeltsBand;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * One learner's sitting of an IELTS full test (docs/decisions.md, D-IELTS-6 and D-IELTS-10). It holds no answers of its
 * own: the Listening + Reading sitting lives in exam_attempts and the five Writing and Speaking answers in the
 * assessment module. This row ties them to one test and works out the bands.
 * <p>
 * The rules: a part counts only if it was submitted within {@link #WINDOW} of starting - the deadline covers
 * submitting, so an answer graded later still counts; once a part is attached it is never swapped for another; the
 * provisional band is the lower of Listening and Reading; the overall band exists only when all four skills are graded;
 * a sitting still short of a grade {@link #GRADING_WINDOW} after its deadline expires.
 */
@Entity
@Table(name = "full_test_sittings")
@Getter
public class FullTestSitting extends BasePersistedEntity {

    /** IELTS itself allows the Speaking test up to seven days either side of the other three. */
    public static final Duration WINDOW = Duration.ofDays(7);

    /**
     * How long grading may take once the submit window has closed. A grade that never comes - an answer the grader gave
     * up on and nobody resent, a review nobody picked up - would otherwise hold the sitting open for good; past this
     * the sitting expires and keeps its provisional band, so the learner can start again.
     */
    public static final Duration GRADING_WINDOW = Duration.ofDays(7);

    /** An answer's submission time and grade's actual save time; reconciliation may happen after either deadline. */
    public record Part(UUID attemptId, Instant submittedAt, BigDecimal band, Instant assessedAt, long version) {
        public Part(UUID attemptId, Instant submittedAt, BigDecimal band, Instant assessedAt) {
            this(attemptId, submittedAt, band, assessedAt, 0L);
        }
    }

    /**
     * Everything the service found, in plain values.
     *
     * @param parts
     *            the five Writing and Speaking slots in {@link FullTest.Slot} order; a null entry when nothing was
     *            found
     */
    public record Progress(UUID examAttemptId, Instant examSubmittedAt, BigDecimal listening, BigDecimal reading,
            List<Part> parts) {
    }

    /** What a {@link #settle} changed that anyone outside needs to know about. */
    public record Change(boolean provisionalSet, boolean completed) {
    }

    @Column(name = "full_test_id", nullable = false, updatable = false)
    private UUID fullTestId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "exam_attempt_id")
    private UUID examAttemptId;

    @Column(name = "writing1_attempt_id")
    private UUID writing1AttemptId;

    @Column(name = "writing2_attempt_id")
    private UUID writing2AttemptId;

    @Column(name = "speaking1_attempt_id")
    private UUID speaking1AttemptId;

    @Column(name = "speaking2_attempt_id")
    private UUID speaking2AttemptId;

    @Column(name = "speaking3_attempt_id")
    private UUID speaking3AttemptId;

    @Column(name = "listening_band")
    private BigDecimal listeningBand;

    @Column(name = "reading_band")
    private BigDecimal readingBand;

    @Column(name = "writing_band")
    private BigDecimal writingBand;

    @Column(name = "speaking_band")
    private BigDecimal speakingBand;

    @Column(name = "provisional_band")
    private BigDecimal provisionalBand;

    @Column(name = "overall_band")
    private BigDecimal overallBand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FullTestSittingStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false, updatable = false)
    private Instant deadlineAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;
    private int bandRevision;
    private Instant revisedAt;
    private Instant lastReconciledAt;
    private long assessmentVersionSum = -1;

    public void reconciledAt(Instant now) {
        lastReconciledAt = now;
    }

    public java.util.List<BigDecimal> bandSnapshot() {
        return java.util.Arrays.asList(listeningBand, readingBand, writingBand, speakingBand, overallBand);
    }

    protected FullTestSitting() {
    }

    public static FullTestSitting start(UUID fullTestId, UUID userId, Instant now) {
        FullTestSitting sitting = new FullTestSitting();
        sitting.id = UUID.randomUUID();
        sitting.fullTestId = fullTestId;
        sitting.userId = userId;
        sitting.status = FullTestSittingStatus.IN_PROGRESS;
        sitting.startedAt = now;
        sitting.deadlineAt = now.plus(WINDOW);
        return sitting;
    }

    /** Grades landing after this no longer count: the sitting expires instead of waiting on. */
    public Instant gradesDueAt() {
        return deadlineAt.plus(GRADING_WINDOW);
    }

    public boolean isSettled() {
        return status == FullTestSittingStatus.COMPLETED || status == FullTestSittingStatus.EXPIRED;
    }

    /** The five Writing and Speaking attempts in {@link FullTest.Slot} order, null where nothing is attached yet. */
    public List<UUID> partAttemptIds() {
        return java.util.Arrays.asList(writing1AttemptId, writing2AttemptId, speaking1AttemptId, speaking2AttemptId,
                speaking3AttemptId);
    }

    /**
     * Takes in what the service found and moves the sitting on. Safe to call any number of times with the same
     * findings: nothing already attached or decided changes.
     */
    public Change settle(Progress progress, Instant now) {
        if (status == FullTestSittingStatus.EXPIRED) {
            return new Change(false, false);
        }
        boolean wasCompleted = status == FullTestSittingStatus.COMPLETED;
        var previous = bandSnapshot();
        assessmentVersionSum = progress.parts().stream().filter(java.util.Objects::nonNull).mapToLong(Part::version)
                .sum();
        attachPaper(progress);
        List<UUID> attached = partAttemptIds();
        UUID[] parts = attached.toArray(UUID[]::new);
        BigDecimal[] partBands = new BigDecimal[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Part found = progress.parts().get(i);
            if (found == null || found.attemptId() == null) {
                continue;
            }
            if (parts[i] == null && inTime(found.submittedAt())) {
                parts[i] = found.attemptId();
            }
            if (Objects.equals(parts[i], found.attemptId()) && found.assessedAt() != null
                    && (wasCompleted || !found.assessedAt().isAfter(gradesDueAt()))) {
                partBands[i] = found.band();
            }
        }
        writing1AttemptId = parts[0];
        writing2AttemptId = parts[1];
        speaking1AttemptId = parts[2];
        speaking2AttemptId = parts[3];
        speaking3AttemptId = parts[4];

        boolean provisionalSet = false;
        if (provisionalBand == null && listeningBand != null && readingBand != null) {
            provisionalBand = listeningBand.min(readingBand);
            provisionalSet = true;
        }
        if (partBands[0] != null && partBands[1] != null) {
            writingBand = IeltsBand.writing(partBands[0], partBands[1]);
        }
        if (partBands[2] != null && partBands[3] != null && partBands[4] != null) {
            speakingBand = IeltsBand.mean(List.of(partBands[2], partBands[3], partBands[4]));
        }
        if (listeningBand != null && readingBand != null && writingBand != null && speakingBand != null) {
            overallBand = IeltsBand.mean(List.of(listeningBand, readingBand, writingBand, speakingBand));
            if (wasCompleted) {
                if (previous.equals(bandSnapshot()))
                    return new Change(false, false);
                bandRevision++;
                revisedAt = now;
                return new Change(false, true);
            }
            status = FullTestSittingStatus.COMPLETED;
            completedAt = now;
            return new Change(provisionalSet, true);
        }
        if (now.isAfter(deadlineAt)) {
            boolean everythingIn = examAttemptId != null && java.util.Arrays.stream(parts).allMatch(Objects::nonNull);
            status = everythingIn && !now.isAfter(gradesDueAt()) ? FullTestSittingStatus.AWAITING_GRADES
                    : FullTestSittingStatus.EXPIRED;
        }
        return new Change(provisionalSet, false);
    }

    private void attachPaper(Progress progress) {
        if (examAttemptId == null && progress.examAttemptId() != null && inTime(progress.examSubmittedAt())) {
            examAttemptId = progress.examAttemptId();
        }
        if (examAttemptId != null && examAttemptId.equals(progress.examAttemptId())) {
            listeningBand = listeningBand == null ? progress.listening() : listeningBand;
            readingBand = readingBand == null ? progress.reading() : readingBand;
        }
    }

    private boolean inTime(Instant submittedAt) {
        return submittedAt != null && !submittedAt.isBefore(startedAt) && !submittedAt.isAfter(deadlineAt);
    }
}
