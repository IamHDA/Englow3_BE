package com.englow3.flashcard.dto.command;

import java.util.UUID;

import com.englow3.flashcard.entity.ReviewRating;

public record RateFlashcardCommand(UUID flashcardId, ReviewRating rating, int timeSpentSeconds) {
}
