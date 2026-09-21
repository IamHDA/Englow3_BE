package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.learning.service.DictationScorer.Score;

/**
 * What counts as a correctly transcribed word. The interface highlights differences itself, but the number it shows is
 * this one, so this is the only definition that matters.
 */
class DictationScorerTest {

    private static final String EXPECTED = "The cat sat on the mat.";

    @Nested
    class Accuracy {

        @Test
        void marksAPerfectTranscriptionAsFull() {
            Score score = DictationScorer.score(EXPECTED, "The cat sat on the mat.");

            assertThat(score.accuracyPercent()).isEqualByComparingTo("100.00");
            assertThat(score.correctWordCount()).isEqualTo(6);
            assertThat(score.totalWordCount()).isEqualTo(6);
        }

        /** A learner hears no commas. Marking punctuation would be marking something they were never given. */
        @Test
        void ignoresPunctuationAndCase() {
            Score score = DictationScorer.score(EXPECTED, "the CAT sat, on the mat!");

            assertThat(score.accuracyPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        void scoresAPartialTranscription() {
            Score score = DictationScorer.score(EXPECTED, "The cat sat on a mat");

            assertThat(score.correctWordCount()).isEqualTo(5);
            assertThat(score.accuracyPercent()).isEqualByComparingTo("83.33");
        }

        @Test
        void scoresAnEmptyAnswerAsZeroRatherThanFailing() {
            Score score = DictationScorer.score(EXPECTED, "");

            assertThat(score.accuracyPercent()).isEqualByComparingTo("0.00");
            assertThat(score.totalWordCount()).isEqualTo(6);
        }
    }

    @Nested
    class Ordering {

        /**
         * The reason words are compared position by position rather than as a set. Transcription is about hearing a
         * line in order; a set comparison would call this a perfect score.
         */
        @Test
        void refusesToRewardTheRightWordsInTheWrongOrder() {
            Score score = DictationScorer.score(EXPECTED, "mat the on sat cat the");

            assertThat(score.accuracyPercent()).isLessThan(java.math.BigDecimal.valueOf(100));
        }

        /** Words the learner invented are not correct, and the ones they missed simply never match. */
        @Test
        void doesNotCreditExtraWords() {
            Score score = DictationScorer.score("The cat sat", "The cat sat on the mat");

            assertThat(score.correctWordCount()).isEqualTo(3);
            assertThat(score.totalWordCount()).isEqualTo(3);
            assertThat(score.accuracyPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        void countsAMissedWordAgainstTheLearner() {
            Score score = DictationScorer.score(EXPECTED, "The cat sat on the");

            assertThat(score.correctWordCount()).isEqualTo(5);
            assertThat(score.totalWordCount()).isEqualTo(6);
        }
    }

    @Nested
    class WordSplitting {

        @Test
        void collapsesRepeatedSpacing() {
            assertThat(DictationScorer.words("  The   cat  sat ")).containsExactly("the", "cat", "sat");
        }

        @Test
        void treatsNullAndBlankAsNoWords() {
            assertThat(DictationScorer.words(null)).isEmpty();
            assertThat(DictationScorer.words("   ")).isEmpty();
        }
    }
}
