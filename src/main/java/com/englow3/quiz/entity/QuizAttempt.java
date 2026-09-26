package com.englow3.quiz.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "quiz_attempts")
@Getter
public class QuizAttempt {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizAttemptStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private BigDecimal score;

    @Column(name = "max_score", nullable = false, updatable = false)
    private BigDecimal maxScore;

    @Column(name = "score_percentage")
    private BigDecimal scorePercentage;

    @Column(name = "correct_answer_count")
    private Integer correctAnswerCount;

    @Column(name = "question_count", nullable = false, updatable = false)
    private int questionCount;

    private Boolean passed;

    protected QuizAttempt() {
    }

    public static QuizAttempt start(Quiz quiz, UUID userId, int questionCount, BigDecimal maxScore, Instant now) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.id = UUID.randomUUID();
        attempt.quizId = quiz.getId();
        attempt.userId = userId;
        attempt.status = QuizAttemptStatus.IN_PROGRESS;
        attempt.startedAt = now;
        attempt.expiresAt = now.plusSeconds(quiz.getTimeLimitSeconds());
        attempt.questionCount = questionCount;
        attempt.maxScore = maxScore;
        return attempt;
    }

    /**
     * The pass mark is a percentage rather than a raw score, so a quiz can gain a question without its threshold
     * silently becoming easier or harder.
     */
    public void score(BigDecimal score, int correctAnswerCount, short passingScorePercent, Instant now) {
        this.score = score;
        this.correctAnswerCount = correctAnswerCount;
        this.scorePercentage = maxScore.signum() == 0 ? BigDecimal.ZERO
                : score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
        this.passed = this.scorePercentage.compareTo(BigDecimal.valueOf(passingScorePercent)) >= 0;
        this.submittedAt = now;
        this.status = QuizAttemptStatus.SCORED;
    }

    public void expire() {
        this.status = QuizAttemptStatus.EXPIRED;
    }
}
