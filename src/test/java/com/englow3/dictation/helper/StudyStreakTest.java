package com.englow3.dictation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class StudyStreakTest {

    @Test
    void keepsYesterdayStreakAndStopsAtGap() {
        LocalDate today = LocalDate.of(2026, 9, 26);

        assertThat(StudyStreak.count(List.of(today.minusDays(1), today.minusDays(2), today.minusDays(4)), today))
                .isEqualTo(2);
    }
}
