package com.englow3.quiz.entity;

/** Same lifecycle as {@link FlashcardSetStatus}, and a separate enum for the same reason. */
public enum QuizStatus {
    DRAFT, PENDING_REVIEW, REJECTED, PUBLISHED, ARCHIVED
}
