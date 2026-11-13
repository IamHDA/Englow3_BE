package com.englow3.progress.helper;

import java.time.LocalDate;
import java.util.List;

/**
 * How many days in a row the learner has studied. Pure, so the awkward cases can be pinned down without a database:
 * what happens when they studied yesterday but not yet today, and what happens at a gap.
 */
public final class StudyStreak {

    private StudyStreak() {
    }

    /**
     * Counts back from today through consecutive days. A learner who studied yesterday but has not started today still
     * has their streak: the day is not over, and breaking it at midnight would punish them for being asked before they
     * sat down. One clear day with nothing ends it.
     *
     * @param studyDays
     *            distinct days with activity, newest first
     * @param today
     *            the learner's current date
     */
    public static int count(List<LocalDate> studyDays, LocalDate today) {
        if (studyDays.isEmpty()) {
            return 0;
        }

        LocalDate mostRecent = studyDays.get(0);
        if (mostRecent.isBefore(today.minusDays(1))) {
            return 0;
        }

        int streak = 0;
        LocalDate expected = mostRecent;
        for (LocalDate day : studyDays) {
            if (day.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (day.isBefore(expected)) {
                // A gap. Everything older is a previous streak, not this one.
                break;
            }
            // Duplicates cannot occur - the query returns distinct days - but a repeat would simply be skipped rather
            // than counted twice.
        }
        return streak;
    }
}
