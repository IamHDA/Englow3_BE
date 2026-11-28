package com.englow3.flashcard.dto.command;

import java.util.Locale;

import com.englow3.shared.error.BadRequestException;

/**
 * Which shelf of sets a learner is browsing: the reviewed catalogue, the sets they made themselves, or what other
 * learners made and let them see - public sets plus those shared with them as a selected friend.
 */
public enum FlashcardSetScope {
    CATALOGUE, MINE, COMMUNITY;

    public static FlashcardSetScope parse(String value) {
        if (value == null || value.isBlank()) {
            return CATALOGUE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new BadRequestException("FLASHCARD_SCOPE_INVALID", "Unknown flashcard set scope");
        }
    }
}
