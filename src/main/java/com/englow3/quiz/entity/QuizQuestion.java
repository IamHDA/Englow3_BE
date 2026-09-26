package com.englow3.quiz.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "quiz_questions")
@Getter
public class QuizQuestion {

    @Id
    private UUID id;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuizQuestionType questionType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private short points;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "before_text")
    private String beforeText;

    @Column(name = "after_text")
    private String afterText;

    @Column(name = "original_sentence")
    private String originalSentence;

    @Column(name = "rewrite_keyword")
    private String rewriteKeyword;

    protected QuizQuestion() {
    }

    public static QuizQuestion of(UUID quizId, int orderNo, QuizQuestionType questionType, String title, String prompt,
            short points, String explanation, String beforeText, String afterText, String originalSentence,
            String rewriteKeyword) {
        QuizQuestion question = new QuizQuestion();
        question.id = UUID.randomUUID();
        question.quizId = quizId;
        question.orderNo = orderNo;
        question.questionType = questionType;
        question.title = title;
        question.prompt = prompt;
        question.points = points;
        question.explanation = explanation == null ? "" : explanation;
        question.beforeText = beforeText;
        question.afterText = afterText;
        question.originalSentence = originalSentence;
        question.rewriteKeyword = rewriteKeyword;
        return question;
    }
}
