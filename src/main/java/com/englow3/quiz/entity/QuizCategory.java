package com.englow3.quiz.entity;

import java.util.Optional;

/**
 * What a quiz practises. A fixed list rather than free text: "Grammar", "grammar" and "Ngữ pháp" were three filters on
 * the catalogue, and the learning roadmap has to know which skill a quiz trains to place it in a stage.
 */
public enum QuizCategory {
    GRAMMAR, VOCABULARY, READING, MIXED;

    /** A filter value from a query string; anything that is not a category matches nothing rather than failing. */
    public static Optional<QuizCategory> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim()));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
