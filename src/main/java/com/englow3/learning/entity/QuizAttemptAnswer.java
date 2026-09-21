package com.englow3.learning.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "quiz_attempt_answers")
@Getter
public class QuizAttemptAnswer {

    @Id
    private UUID id;

    @Column(name = "quiz_attempt_id", nullable = false, updatable = false)
    private UUID quizAttemptId;

    @Column(name = "quiz_question_id", nullable = false, updatable = false)
    private UUID quizQuestionId;

    @Column(nullable = false)
    private String response;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "awarded_points", nullable = false)
    private BigDecimal awardedPoints;

    protected QuizAttemptAnswer() {
    }

    public static QuizAttemptAnswer graded(UUID attemptId, UUID questionId, String response, boolean correct,
            BigDecimal awardedPoints) {
        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.id = UUID.randomUUID();
        answer.quizAttemptId = attemptId;
        answer.quizQuestionId = questionId;
        answer.response = response == null ? "" : response;
        answer.correct = correct;
        answer.awardedPoints = awardedPoints;
        return answer;
    }
}
