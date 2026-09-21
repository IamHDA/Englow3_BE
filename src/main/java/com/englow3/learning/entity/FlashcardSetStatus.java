package com.englow3.learning.entity;

/**
 * Deliberately not the exam module's {@code ExamStatus}, even though the values match today: a paper's lifecycle and a
 * vocabulary set's lifecycle are different concerns owned by different modules, and sharing one enum would tie a change
 * in either to the other.
 */
public enum FlashcardSetStatus {
    DRAFT, PUBLISHED, ARCHIVED
}
