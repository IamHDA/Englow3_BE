package com.englow3.dictation.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.englow3.dictation.helper.MissedWordCounter.MissedWord;

/** Recomputed rather than stored, so the counting rules are worth pinning down. */
class MissedWordCounterTest {

    private static String[] attempt(String expected, String actual) {
        return new String[] { expected, actual };
    }

    @Test
    void reportsNothingWhenEveryWordWasRight() {
        List<MissedWord> missed = MissedWordCounter.count(List.<String[]> of(attempt("The cat sat", "the cat sat")),
                10);

        assertThat(missed).isEmpty();
    }

    @Test
    void countsAWordAcrossSeveralAttempts() {
        List<MissedWord> missed = MissedWordCounter
                .count(List.<String[]> of(attempt("She has been there", "She have been there"),
                        attempt("He has gone", "He have gone"), attempt("It has arrived", "It has arrived")), 10);

        assertThat(missed).singleElement().satisfies(word -> {
            assertThat(word.word()).isEqualTo("has");
            assertThat(word.missedCount()).isEqualTo(2);
            assertThat(word.correctCount()).isEqualTo(1);
            assertThat(word.accuracyPercent()).isEqualTo(33);
        });
    }

    /** A word the learner never reached is missed too - the sentence ended before they typed it. */
    @Test
    void countsAWordTheLearnerNeverTypedAsMissed() {
        List<MissedWord> missed = MissedWordCounter
                .count(List.<String[]> of(attempt("The cat sat down", "The cat sat")), 10);

        assertThat(missed).extracting(MissedWord::word).containsExactly("down");
    }

    /**
     * Ordered by accuracy, not raw misses. A word missed twice out of two is a bigger problem than one missed three
     * times out of thirty, and showing the second first would bury the real gap.
     */
    @Test
    void putsTheWorstAccuracyFirstRatherThanTheMostMisses() {
        List<String[]> attempts = new java.util.ArrayList<>();
        attempts.add(attempt("alpha", "wrong"));
        attempts.add(attempt("alpha", "wrong"));
        for (int i = 0; i < 27; i++) {
            attempts.add(attempt("beta", "beta"));
        }
        for (int i = 0; i < 3; i++) {
            attempts.add(attempt("beta", "wrong"));
        }

        List<MissedWord> missed = MissedWordCounter.count(attempts, 10);

        assertThat(missed).extracting(MissedWord::word).containsExactly("alpha", "beta");
    }

    @Test
    void returnsNoMoreThanTheLimit() {
        List<String[]> attempts = List.<String[]> of(attempt("one two three four five", "x x x x x"));

        assertThat(MissedWordCounter.count(attempts, 3)).hasSize(3);
    }
}
