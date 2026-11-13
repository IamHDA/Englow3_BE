package com.englow3.flashcard.entity;

/**
 * Deliberately not the exam module's {@code ExamStatus}, even though the values match today: a paper's lifecycle and a
 * vocabulary set's lifecycle are different concerns owned by different modules, and sharing one enum would tie a change
 * in either to the other. Same reasoning against sharing with {@link QuizStatus} and {@link DictationLessonStatus}.
 * <p>
 * DRAFT to PENDING_REVIEW to PUBLISHED, with REJECTED as the way back. There is no LOCKED-style dead end: a rejected
 * set is editable, or its author could never answer the note.
 */
public enum FlashcardSetStatus {
    DRAFT, PENDING_REVIEW, REJECTED, PUBLISHED, ARCHIVED
}
