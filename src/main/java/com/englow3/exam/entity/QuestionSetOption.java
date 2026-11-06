package com.englow3.exam.entity;

import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One entry of the list a whole question set shares - a heading "iv", a person "B", a map label "F". Learner-safe on
 * its own: which question it answers lives in {@link QuestionMatchingAnswer}, never here.
 */
@Entity
@Table(name = "question_set_options")
@Getter
public class QuestionSetOption extends BasePersistedEntity {

    @Column(name = "question_set_id", nullable = false)
    private UUID questionSetId;

    @Column(name = "option_label", nullable = false)
    private String label;

    private String content;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    protected QuestionSetOption() {
    }

    public static QuestionSetOption create(UUID questionSetId, String label, String content, int orderNo) {
        QuestionSetOption option = new QuestionSetOption();
        option.id = UUID.randomUUID();
        option.questionSetId = questionSetId;
        option.label = label;
        option.content = content;
        option.orderNo = orderNo;
        return option;
    }
}
