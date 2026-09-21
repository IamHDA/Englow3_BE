package com.englow3.learning.dto.command;

import java.util.UUID;

import com.englow3.learning.entity.ReviewRating;

public record RateFlashcardCommand(UUID flashcardId, ReviewRating rating, int timeSpentSeconds) {
}
