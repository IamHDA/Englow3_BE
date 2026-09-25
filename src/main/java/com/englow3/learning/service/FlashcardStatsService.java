package com.englow3.learning.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.result.FlashcardStatsResult;
import com.englow3.learning.query.FlashcardStatsQuery;
import com.englow3.user.service.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * The flashcard statistics screen. Read-only, and every figure comes from {@code flashcard_review_logs} - the events,
 * not the current state, because "how much did I do last week" is a question only the events can answer.
 */
@Service
@RequiredArgsConstructor
public class FlashcardStatsService {

    /** How far back the difficult-card and history lists look. Enough to be useful, short enough to stay readable. */
    private static final int DIFFICULT_CARD_LIMIT = 10;
    private static final int HISTORY_LIMIT = 20;

    /**
     * The streak is counted over a year regardless of the period the rest of the screen is showing. A learner looking
     * at "last 7 days" still has a streak that may be forty days long, and truncating it to the window would report a
     * number that is simply wrong.
     */
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final FlashcardStatsQuery statsQuery;
    private final UserDirectory userDirectory;

    @Transactional(readOnly = true)
    public FlashcardStatsResult statsFor(int periodDays) {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant from = Instant.now().minus(periodDays, ChronoUnit.DAYS);

        List<LocalDate> studyDays = statsQuery.studyDays(userId,
                Instant.now().minus(STREAK_LOOKBACK_DAYS, ChronoUnit.DAYS));

        FlashcardStatsQuery.PeriodSummary summary = statsQuery.periodSummary(userId, from);
        return new FlashcardStatsResult(periodDays, summary.cardsStudied(), summary.retentionPercent(),
                summary.studySeconds(), StudyStreak.count(studyDays, LocalDate.now(ZoneOffset.UTC)),
                statsQuery.activityByDay(userId, from), statsQuery.difficultCards(userId, DIFFICULT_CARD_LIMIT),
                statsQuery.history(userId, HISTORY_LIMIT));
    }
}
