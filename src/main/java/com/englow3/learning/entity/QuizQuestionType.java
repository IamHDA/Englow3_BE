package com.englow3.learning.entity;

/**
 * The five shapes a quiz question can take. Deliberately not the exam module's {@code QuestionType}: that one is
 * limited to what a graded TOEIC paper can express, while practice questions are free to ask for a rewritten sentence
 * or a reordered one. Sharing the enum would drag the exam catalogue into shapes it cannot grade.
 */
public enum QuizQuestionType {
    MULTIPLE_CHOICE, FILL_BLANK, REWRITE, REORDER, MATCHING
}
