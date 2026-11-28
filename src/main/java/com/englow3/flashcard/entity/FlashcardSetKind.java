package com.englow3.flashcard.entity;

/**
 * Who a set belongs to. A CATALOGUE set is staff content that goes through review before learners see it; a PERSONAL
 * set is a learner's own, never reviewed, and shown only to whom its {@link FlashcardSetVisibility} allows.
 */
public enum FlashcardSetKind {
    CATALOGUE, PERSONAL
}
