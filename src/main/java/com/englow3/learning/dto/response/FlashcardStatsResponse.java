package com.englow3.learning.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.englow3.learning.dto.result.FlashcardStatsResult;

public record FlashcardStatsResponse(int periodDays, long cardsStudied, int retentionPercent, long studySeconds,
        int streakDays, List<DailyActivityResponse> activity, List<DifficultCardResponse> difficultCards,
        List<SessionSummaryResponse> history) {

    public static FlashcardStatsResponse from(FlashcardStatsResult result) {
        return new FlashcardStatsResponse(result.periodDays(), result.cardsStudied(), result.retentionPercent(),
                result.studySeconds(), result.streakDays(),
                result.activity().stream().map(day -> new DailyActivityResponse(day.day(), day.cardCount())).toList(),
                result.difficultCards().stream()
                        .map(card -> new DifficultCardResponse(card.flashcardId(), card.lemma(), card.setName(),
                                card.lapseCount(), card.lastReviewed()))
                        .toList(),
                result.history().stream().map(session -> new SessionSummaryResponse(session.day(), session.setId(),
                        session.setName(), session.cardCount(), session.recallPercent(), session.studySeconds()))
                        .toList());
    }

    public record DailyActivityResponse(LocalDate day, long cardCount) {
    }

    public record DifficultCardResponse(UUID flashcardId, String lemma, String setName, int lapseCount,
            Instant lastReviewed) {
    }

    public record SessionSummaryResponse(LocalDate day, UUID setId, String setName, long cardCount, int recallPercent,
            long studySeconds) {
    }
}
