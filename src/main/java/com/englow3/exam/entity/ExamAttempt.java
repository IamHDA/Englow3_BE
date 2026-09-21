package com.englow3.exam.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
        return attempt;
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
