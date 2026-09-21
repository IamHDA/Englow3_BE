package com.englow3.learning.dto.request;

import com.englow3.learning.entity.ReviewRating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The time cap is not a business rule but a sanity bound: a learner who leaves a card open overnight would otherwise
 * report eight hours on one word and skew every average built on it.
 */
public record RateFlashcardRequest(@NotNull ReviewRating rating, @PositiveOrZero @Max(3600) int timeSpentSeconds) {
}
