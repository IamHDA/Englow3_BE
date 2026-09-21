package com.englow3.exam.entity;

import java.math.BigDecimal;
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
@Table(name = "attempt_answers")
@Getter
public class AttemptAnswer {

    @Id
    private UUID id;

    @Column(name = "exam_attempt_id", nullable = false, updatable = false)
    private UUID examAttemptId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "awarded_raw_score", nullable = false)
    private BigDecimal awardedRawScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false)
    private AnswerGradingStatus gradingStatus;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt;

    protected AttemptAnswer() {
    }

    public static AttemptAnswer graded(UUID attemptId, UUID questionId, boolean correct, BigDecimal awardedRawScore,
            Instant now) {
        AttemptAnswer answer = new AttemptAnswer();
        answer.id = UUID.randomUUID();
        answer.examAttemptId = attemptId;
        answer.questionId = questionId;
        answer.correct = correct;
        answer.awardedRawScore = awardedRawScore;
        answer.gradingStatus = AnswerGradingStatus.GRADED;
        answer.answeredAt = now;
        answer.gradedAt = now;
        return answer;
    }
}
