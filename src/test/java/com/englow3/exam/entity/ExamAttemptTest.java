package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExamAttemptTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    /** A paper that declares 9 (a band scale) over questions worth 80 in all. */
    private static Exam bandScaledPaper() {
        return Exam.draft("IELTS Test", "Band scale", ExamType.MOCK, null, null, TargetLevel.B2, 7200,
                new BigDecimal("9.00"), new BigDecimal("6.5"), UUID.randomUUID());
    }

    /**
     * A full attempt is scored out of what the questions carry. Out of the declared 9, eighty correct answers read as
     * 888% - the figure a learner saw as a completion rate above a hundred.
     */
    @Test
    void scoresAFullAttemptOutOfWhatTheQuestionsAreWorth() {
        ExamAttempt attempt = ExamAttempt.start(bandScaledPaper(), UUID.randomUUID(), 80, new BigDecimal("80"), NOW);

        attempt.score(new BigDecimal("80"), 80, NOW.plusSeconds(600));

        assertThat(attempt.getMaxRawScore()).isEqualByComparingTo("80");
        assertThat(attempt.getScorePercentage()).isEqualByComparingTo("100");
    }

    @Test
    void neverReportsMoreThanAHundredPercent() {
        ExamAttempt attempt = ExamAttempt.start(bandScaledPaper(), UUID.randomUUID(), 80, NOW);

        attempt.score(new BigDecimal("80"), 80, NOW.plusSeconds(600));

        assertThat(attempt.getScorePercentage()).isEqualByComparingTo("100");
    }

    /** A practice can be left and resumed; only the real test ends when the learner goes. */
    @Test
    void aPracticeIsNeverAbandoned() {
        ExamAttempt practice = ExamAttempt.startPractice(bandScaledPaper(), UUID.randomUUID(),
                java.util.Set.of(UUID.randomUUID()), 5, new BigDecimal("5"), null, NOW);

        assertThat(practice.isAbandoned(NOW.plusSeconds(86_400), java.time.Duration.ofMinutes(2))).isFalse();
    }

    @Test
    void aFullSittingIsAbandonedOnlyOnceTheGraceHasPassedSinceItWasLastSeen() {
        ExamAttempt attempt = ExamAttempt.start(bandScaledPaper(), UUID.randomUUID(), 80, NOW);
        java.time.Duration grace = java.time.Duration.ofMinutes(2);

        assertThat(attempt.isAbandoned(NOW.plusSeconds(119), grace)).isFalse();
        assertThat(attempt.isAbandoned(NOW.plusSeconds(120), grace)).isTrue();

        attempt.touch(NOW.plusSeconds(100));
        assertThat(attempt.isAbandoned(NOW.plusSeconds(219), grace)).isFalse();
        assertThat(attempt.isAbandoned(NOW.plusSeconds(220), grace)).isTrue();
    }
}
