package com.englow3.dictation.helper;

import java.time.LocalDate;
import java.util.List;

/** Dictation-only streak semantics; deliberately local so this module does not depend on progress internals. */
public final class StudyStreak {

    private StudyStreak() {
    }

    public static int count(List<LocalDate> studyDays, LocalDate today) {
        if (studyDays.isEmpty() || studyDays.get(0).isBefore(today.minusDays(1))) {
            return 0;
        }

        int streak = 0;
        LocalDate expected = studyDays.get(0);
        for (LocalDate day : studyDays) {
            if (day.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (day.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }
}
