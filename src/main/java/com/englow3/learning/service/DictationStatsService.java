package com.englow3.learning.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.result.DictationStatsResult;
import com.englow3.learning.dto.result.MistakeQueueResult;
import com.englow3.learning.query.DictationStatsQuery;
import com.englow3.shared.persistence.ParallelReads;
import com.englow3.user.service.UserDirectory;

import lombok.RequiredArgsConstructor;

/** The dictation statistics screen. Read-only, built from dictation_attempts. */
@Service
@RequiredArgsConstructor
public class DictationStatsService {

    private static final int DIFFICULT_SENTENCE_LIMIT = 10;
    private static final int MISSED_WORD_LIMIT = 12;
    private static final int HISTORY_LIMIT = 20;

    /**
     * How many recent answers the missed-word count looks at. Bounded because it re-derives a per-word breakdown that
     * is not stored - unbounded, one learner with a long history would walk their whole transcript on every page load.
     */
    private static final int MISSED_WORD_SAMPLE = 200;

    private static final int STREAK_LOOKBACK_DAYS = 365;

    /** How many lines the review queue hands out in one sitting. More is a backlog, not a session. */
    private static final int MISTAKE_QUEUE_LIMIT = 10;

    private final DictationStatsQuery statsQuery;
    private final UserDirectory userDirectory;
    private final ParallelReads reads;

    /** Not transactional: the nine reads are independent and run side by side, see {@link ParallelReads}. */
    public DictationStatsResult statsFor(int periodDays) {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant from = Instant.now().minus(periodDays, ChronoUnit.DAYS);

        var attempts = reads.fork(() -> statsQuery.recentAttemptTexts(userId, from, MISSED_WORD_SAMPLE));
        var practiceDays = reads.fork(
                () -> statsQuery.practiceDays(userId, Instant.now().minus(STREAK_LOOKBACK_DAYS, ChronoUnit.DAYS)));
        var lessonsCompleted = reads
                .fork(() -> statsQuery.lessonsCompleted(userId, DictationScorer.COMPLETION_THRESHOLD));
        var averageAccuracy = reads.fork(() -> statsQuery.averageAccuracy(userId, from));
        var listeningSeconds = reads.fork(() -> statsQuery.listeningSeconds(userId, from));
        var sentencesPractised = reads.fork(() -> statsQuery.sentencesPractised(userId, from));
        var accuracyByDay = reads.fork(() -> statsQuery.accuracyByDay(userId, from));
        var difficultSentences = reads.fork(() -> statsQuery.difficultSentences(userId, DIFFICULT_SENTENCE_LIMIT));
        var history = reads.fork(() -> statsQuery.history(userId, HISTORY_LIMIT));

        List<String[]> attemptPairs = attempts.get().stream()
                .map(attempt -> new String[] { attempt.expected(), attempt.actual() }).toList();

        return new DictationStatsResult(periodDays, lessonsCompleted.get(), averageAccuracy.get(),
                listeningSeconds.get(), sentencesPractised.get(),
                StudyStreak.count(practiceDays.get(), LocalDate.now(ZoneOffset.UTC)), accuracyByDay.get(),
                MissedWordCounter.count(attemptPairs, MISSED_WORD_LIMIT), difficultSentences.get(), history.get());
    }

    /**
     * Lines to practise again, worst first, across every lesson. Uses the same threshold as everything else that
     * decides what "cleared" means, so a sentence cannot be finished on one screen and outstanding on another.
     */
    @Transactional(readOnly = true)
    public MistakeQueueResult mistakeQueue() {
        UUID userId = userDirectory.requireCurrentUserId();

        return new MistakeQueueResult(
                statsQuery.mistakeQueue(userId, DictationScorer.COMPLETION_THRESHOLD, MISTAKE_QUEUE_LIMIT));
    }
}
