package com.englow3.exam.entity;

import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One answer choice. See {@link ExamSection} for why there is no association to its parent, no setter, and a
 * {@code create(...)} factory instead. Both {@code correct} and {@code explanation} are answer-key data: the admin tree
 * load returns them, the sitting's must not.
 */
@Entity
@Table(name = "question_options")
@Getter
public class QuestionOption extends BasePersistedEntity {

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private String content;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    private String explanation;

    protected QuestionOption() {
    }

    public static QuestionOption create(UUID questionId, String content, int orderNo, boolean correct,
            String explanation) {
        QuestionOption option = new QuestionOption();
        option.id = UUID.randomUUID();
        option.questionId = questionId;
        option.content = content;
        option.orderNo = orderNo;
        option.correct = correct;
        option.explanation = explanation;
        return option;
    }
}
