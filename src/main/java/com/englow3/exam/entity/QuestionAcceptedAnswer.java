package com.englow3.exam.entity;

import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One spelling a {@code TEXT_ENTRY} question accepts. Every variant the author wants to count - British and American
 * spelling, a number in digits and in words, an answer with and without an optional word - is a row of its own: the
 * grader only folds case and whitespace, so what counts is decided by the data, not guessed by code. Answer-key data:
 * the admin tree returns it, the sitting never does.
 */
@Entity
@Table(name = "question_accepted_answers")
@Getter
public class QuestionAcceptedAnswer extends BasePersistedEntity {

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    @Column(name = "is_case_sensitive", nullable = false)
    private boolean caseSensitive;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    protected QuestionAcceptedAnswer() {
    }

    public static QuestionAcceptedAnswer create(UUID questionId, String answerText, boolean caseSensitive,
            int orderNo) {
        QuestionAcceptedAnswer answer = new QuestionAcceptedAnswer();
        answer.id = UUID.randomUUID();
        answer.questionId = questionId;
        answer.answerText = answerText;
        answer.caseSensitive = caseSensitive;
        answer.orderNo = orderNo;
        return answer;
    }
}
