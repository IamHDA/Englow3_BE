package com.englow3.flashcard.entity;

/**
 * What the learner said about a card they had just seen. SM-2 grades answers 0-5; these four are the buttons the
 * interface actually offers, and {@code FlashcardSrs} maps them onto the algorithm. Only AGAIN counts as a failure.
 */
public enum ReviewRating {
    AGAIN, HARD, GOOD, EASY;

    public boolean isFailure() {
        return this == AGAIN;
    }
}
