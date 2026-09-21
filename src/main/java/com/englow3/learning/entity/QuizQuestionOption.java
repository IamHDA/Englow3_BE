package com.englow3.learning.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "quiz_question_options")
@Getter
public class QuizQuestionOption {

    @Id
    private UUID id;

    @Column(name = "quiz_question_id", nullable = false, updatable = false)
    private UUID quizQuestionId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean correct;

    protected QuizQuestionOption() {
    }
}
