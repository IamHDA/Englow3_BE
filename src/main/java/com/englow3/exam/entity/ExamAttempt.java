package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "exam_attempts")
@Getter
public class ExamAttempt {

    @Id
    private UUID id;

    @Column(name = "exam_id", nullable = false, updatable = false)
    private UUID examId;

    @Column(name = "exam_version_number", nullable = false, updatable = false)
    private int examVersionNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamAttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "scored_at")
    private Instant scoredAt;

    @Column(name = "raw_score")
    private BigDecimal rawScore;

    @Column(name = "max_raw_score", nullable = false, updatable = false)
    private BigDecimal maxRawScore;

    @Column(name = "converted_score")
    private BigDecimal convertedScore;

    @Column(name = "score_percentage")
    private BigDecimal scorePercentage;

    @Column(name = "correct_answer_count")
    private Integer correctAnswerCount;

    @Column(name = "question_count", nullable = false, updatable = false)
    private int questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExamAttemptMode mode;

    /** The clock this attempt runs on; null only for an untimed practice. */
    @Column(name = "time_limit_seconds", updatable = false)
    private Integer timeLimitSeconds;

    /** The parts a practice covers. Empty for a full attempt, which covers the whole paper. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exam_attempt_parts", joinColumns = @JoinColumn(name = "exam_attempt_id"))
    @Column(name = "section_part_id", nullable = false)
    private Set<UUID> partIds = new HashSet<>();

    /**
     * How long an untimed practice stays open. It still needs a deadline: an abandoned practice is then finalized by
     * the deadline worker like any other attempt, instead of blocking the next start on that paper forever.
     */
    public static final Duration UNTIMED_PRACTICE_WINDOW = Duration.ofHours(24);

    protected ExamAttempt() {
    }

    public static ExamAttempt start(Exam exam, UUID userId, int questionCount, Instant now) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.id = UUID.randomUUID();
        attempt.examId = exam.getId();
        attempt.examVersionNumber = exam.getVersionNumber();
        attempt.userId = userId;
        attempt.status = ExamAttemptStatus.IN_PROGRESS;
        attempt.startedAt = now;
        attempt.expiresAt = now.plusSeconds(exam.getDurationSeconds());
        attempt.maxRawScore = exam.getMaxRawScore();
        attempt.questionCount = questionCount;
        attempt.mode = ExamAttemptMode.FULL;
        attempt.timeLimitSeconds = exam.getDurationSeconds();
        return attempt;
    }

    /**
     * A practice over some of the paper's parts. Its totals are those of the chosen parts, not the paper's - a practice
     * of one part scored out of the whole paper's maximum would read as a failure however well it went.
     */
    public static ExamAttempt startPractice(Exam exam, UUID userId, Collection<UUID> partIds, int questionCount,
            BigDecimal maxRawScore, Integer timeLimitSeconds, Instant now) {
        if (partIds.isEmpty()) {
            throw new IllegalArgumentException("A practice covers at least one part");
        }
        ExamAttempt attempt = new ExamAttempt();
        attempt.id = UUID.randomUUID();
        attempt.examId = exam.getId();
        attempt.examVersionNumber = exam.getVersionNumber();
        attempt.userId = userId;
        attempt.status = ExamAttemptStatus.IN_PROGRESS;
        attempt.startedAt = now;
        attempt.expiresAt = timeLimitSeconds == null ? now.plus(UNTIMED_PRACTICE_WINDOW)
                : now.plusSeconds(timeLimitSeconds);
        attempt.maxRawScore = maxRawScore;
        attempt.questionCount = questionCount;
        attempt.mode = ExamAttemptMode.PRACTICE;
        attempt.timeLimitSeconds = timeLimitSeconds;
        attempt.partIds = new HashSet<>(partIds);
        return attempt;
    }

    public boolean isPractice() {
        return mode == ExamAttemptMode.PRACTICE;
    }

    /** Whether a start asking for this mode, these parts and this clock would get this very attempt back. */
    public boolean matches(ExamAttemptMode requestedMode, Set<UUID> requestedParts, Integer requestedLimitSeconds) {
        if (mode != requestedMode) {
            return false;
        }
        if (mode == ExamAttemptMode.FULL) {
            return true;
        }
        return partIds.equals(requestedParts) && java.util.Objects.equals(timeLimitSeconds, requestedLimitSeconds);
    }

    public void score(BigDecimal rawScore, int correctAnswerCount, Instant now) {
        if (status != ExamAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("ATTEMPT_ALREADY_FINALIZED", "This exam attempt has already been finalized");
        }
        this.rawScore = rawScore;
        this.scorePercentage = maxRawScore.signum() == 0 ? BigDecimal.ZERO
                : rawScore.multiply(BigDecimal.valueOf(100)).divide(maxRawScore, 2, java.math.RoundingMode.HALF_UP);
        this.correctAnswerCount = correctAnswerCount;
        this.submittedAt = now;
        this.scoredAt = now;
        this.status = ExamAttemptStatus.SCORED;
    }

    public void expire(Instant now) {
        if (status == ExamAttemptStatus.IN_PROGRESS && !now.isBefore(expiresAt)) {
            status = ExamAttemptStatus.EXPIRED;
        }
    }
}
