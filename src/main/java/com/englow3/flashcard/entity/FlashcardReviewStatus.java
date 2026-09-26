package com.englow3.flashcard.entity;

/**
 * How far a card has got with one learner. Derived from the SM-2 state rather than set by hand, so it cannot drift out
 * of step with the schedule it is meant to describe.
 */
public enum FlashcardReviewStatus {
    NEW, LEARNING, REVIEW, MASTERED
}
