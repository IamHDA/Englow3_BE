package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The edge cases are all about "today", which is why this is a pure function taking one. */
class StudyStreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @Test
    void countsNothingForALearnerWhoHasNeverStudied() {
        assertThat(StudyStreak.count(List.of(), TODAY)).isZero();
    }

    @Test
    void countsConsecutiveDaysEndingToday() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(StudyStreak.count(days, TODAY)).isEqualTo(3);
    }

    /**
     * The case that decides whether the feature feels fair. Asking at breakfast must not report a broken streak for a
     * learner who studied yesterday and has not started today.
     */
    @Test
    void keepsTheStreakAliveBeforeTheLearnerHasStartedToday() {
        List<LocalDate> days = List.of(TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(StudyStreak.count(days, TODAY)).isEqualTo(2);
    }

    @Test
    void endsTheStreakAfterAClearDayWithNothing() {
        List<LocalDate> days = List.of(TODAY.minusDays(2), TODAY.minusDays(3));

        assertThat(StudyStreak.count(days, TODAY)).isZero();
    }

    @Test
    void stopsAtTheFirstGapRatherThanCountingEverything() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(5), TODAY.minusDays(6));

        assertThat(StudyStreak.count(days, TODAY)).isEqualTo(2);
    }

    @Test
    void countsASingleDayOfStudyAsOne() {
        assertThat(StudyStreak.count(List.of(TODAY), TODAY)).isEqualTo(1);
    }
}
