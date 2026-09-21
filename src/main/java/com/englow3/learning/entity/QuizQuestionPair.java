package com.englow3.learning.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "quiz_question_pairs")
@Getter
public class QuizQuestionPair {

    @Id
    private UUID id;

    @Column(name = "quiz_question_id", nullable = false, updatable = false)
    private UUID quizQuestionId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(name = "left_text", nullable = false)
    private String leftText;

    @Column(name = "right_text", nullable = false)
    private String rightText;

    protected QuizQuestionPair() {
    }
}
