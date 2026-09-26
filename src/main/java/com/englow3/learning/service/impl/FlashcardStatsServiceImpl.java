package com.englow3.learning.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.englow3.learning.dto.result.FlashcardStatsResult;
import com.englow3.learning.query.FlashcardStatsQuery;
import com.englow3.shared.persistence.ParallelReads;
import com.englow3.user.api.UserDirectory;
import com.englow3.learning.service.*;

import lombok.RequiredArgsConstructor;

/**
 * The flashcard statistics screen. Read-only, and every figure comes from {@code flashcard_review_logs} - the events,
 * not the current state, because "how much did I do last week" is a question only the events can answer.
 */
@Service
@RequiredArgsConstructor
public class FlashcardStatsServiceImpl implements FlashcardStatsService {

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
    private final ParallelReads reads;

    /** Not transactional: the five reads are independent and run side by side, see {@link ParallelReads}. */
    public FlashcardStatsResult statsFor(int periodDays) {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant from = Instant.now().minus(periodDays, ChronoUnit.DAYS);

        var studyDays = reads
                .fork(() -> statsQuery.studyDays(userId, Instant.now().minus(STREAK_LOOKBACK_DAYS, ChronoUnit.DAYS)));
        var summaryRead = reads.fork(() -> statsQuery.periodSummary(userId, from));
        var activityByDay = reads.fork(() -> statsQuery.activityByDay(userId, from));
        var difficultCards = reads.fork(() -> statsQuery.difficultCards(userId, DIFFICULT_CARD_LIMIT));
        var history = reads.fork(() -> statsQuery.history(userId, HISTORY_LIMIT));

        FlashcardStatsQuery.PeriodSummary summary = summaryRead.get();
        return new FlashcardStatsResult(periodDays, summary.cardsStudied(), summary.retentionPercent(),
                summary.studySeconds(), StudyStreak.count(studyDays.get(), LocalDate.now(ZoneOffset.UTC)),
                activityByDay.get(), difficultCards.get(), history.get());
    }
}
