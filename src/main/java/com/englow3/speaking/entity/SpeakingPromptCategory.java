package com.englow3.speaking.entity;

import java.util.Optional;

/**
 * Which part of pronunciation a prompt works on. A fixed list for the same reason as the quiz categories: free text
 * split one filter into several, and the learning roadmap needs to know what a prompt trains.
 */
public enum SpeakingPromptCategory {
    /** Individual sounds, such as /θ/ against /s/. */
    SOUNDS, WORD_STRESS, INTONATION,
    /** Linking, elision and weak forms across word boundaries. */
    CONNECTED_SPEECH;

    /** A filter value from a query string; anything that is not a category matches nothing rather than failing. */
    public static Optional<SpeakingPromptCategory> parse(String value) {
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
