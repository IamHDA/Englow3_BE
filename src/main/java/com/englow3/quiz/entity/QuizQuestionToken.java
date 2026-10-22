package com.englow3.quiz.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.englow3.shared.persistence.BasePersistedEntity;
import lombok.Getter;

/** One entry of an ordered word list. What the list means is {@link QuizTokenRole}. */
@Entity
@Table(name = "quiz_question_tokens")
@Getter
public class QuizQuestionToken extends BasePersistedEntity {

    @Column(name = "quiz_question_id", nullable = false, updatable = false)
    private UUID quizQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuizTokenRole role;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String value;

    protected QuizQuestionToken() {
    }

    public static QuizQuestionToken of(UUID quizQuestionId, QuizTokenRole role, int orderNo, String value) {
        QuizQuestionToken token = new QuizQuestionToken();
        token.id = UUID.randomUUID();
        token.quizQuestionId = quizQuestionId;
        token.role = role;
        token.orderNo = orderNo;
        token.value = value;
        return token;
    }
}
