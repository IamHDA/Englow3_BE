package com.englow3.exam.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One answer choice. Read-only, see {@link ExamSection}. Both {@code correct} and {@code explanation} are answer-key
 * data: the admin tree load returns them, the sitting's must not.
 */
@Entity
@Table(name = "question_options")
@Getter
public class QuestionOption {

    @Id
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private String content;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    /** The Vietnamese rationale V029 added, one per option. */
    private String explanation;

    protected QuestionOption() {
    }
}
