package com.englow3.flashcard.dto.result;

import java.util.List;

import com.englow3.flashcard.query.FlashcardStatsQuery.DailyActivity;
import com.englow3.flashcard.query.FlashcardStatsQuery.DifficultCard;
import com.englow3.flashcard.query.FlashcardStatsQuery.SessionSummary;

/**
 * Everything the statistics screen shows, in one result. Kept as one shape rather than six endpoints because the screen
 * loads all of it at once, and six round trips to draw one page is the problem a BFF exists to avoid.
 */
public record FlashcardStatsResult(int periodDays, long cardsStudied, int retentionPercent, long studySeconds,
        int streakDays, List<DailyActivity> activity, List<DifficultCard> difficultCards,
        List<SessionSummary> history) {
}
