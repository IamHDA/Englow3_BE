package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.learning.entity.QuizQuestionType;
import com.englow3.learning.service.QuizGrader.GradableQuestion;

/**
 * Five question types, five ways to be wrong. The grader is pure, so each is exercised directly rather than through a
 * submitted attempt.
 */
class QuizGraderTest {

    private static GradableQuestion of(QuizQuestionType type, List<String> optionIds, List<String> accepted,
            List<String> words, List<String> rights) {
        return new GradableQuestion(type, optionIds, accepted, words, rights);
    }

    @Nested
    class MultipleChoice {

        private final GradableQuestion question = of(QuizQuestionType.MULTIPLE_CHOICE, List.of("opt-b"), List.of(),
                List.of(), List.of());

        @Test
        void marksTheChosenOption() {
            assertThat(QuizGrader.isCorrect(question, "opt-b")).isTrue();
            assertThat(QuizGrader.isCorrect(question, "opt-a")).isFalse();
        }

        @Test
        void treatsNoAnswerAsWrongRatherThanAsAnError() {
            assertThat(QuizGrader.isCorrect(question, "")).isFalse();
            assertThat(QuizGrader.isCorrect(question, null)).isFalse();
        }
    }

    @Nested
    class FillBlank {

        private final GradableQuestion question = of(QuizQuestionType.FILL_BLANK, List.of(),
                List.of("has been", "'s been"), List.of(), List.of());

        @Test
        void acceptsAnyOfTheAcceptedForms() {
            assertThat(QuizGrader.isCorrect(question, "has been")).isTrue();
            assertThat(QuizGrader.isCorrect(question, "'s been")).isTrue();
        }

        /** Marking English, not typing: capitals and stray spaces are not mistakes. */
        @Test
        void ignoresCaseAndSurroundingSpace() {
            assertThat(QuizGrader.isCorrect(question, "  Has   Been ")).isTrue();
        }

        @Test
        void rejectsSomethingElse() {
            assertThat(QuizGrader.isCorrect(question, "have been")).isFalse();
        }
    }

    @Nested
    class RewriteAndReorder {

        private final GradableQuestion question = of(QuizQuestionType.REORDER, List.of(), List.of(),
                List.of("She", "has", "never", "been", "there"), List.of());

        @Test
        void requiresTheWordsInOrder() {
            assertThat(QuizGrader.isCorrect(question, "She has never been there")).isTrue();
            assertThat(QuizGrader.isCorrect(question, "She never has been there")).isFalse();
        }

        @Test
        void rejectsAnAnswerMissingAWord() {
            assertThat(QuizGrader.isCorrect(question, "She has never been")).isFalse();
        }

        @Test
        void collapsesRepeatedSpacesBetweenWords() {
            assertThat(QuizGrader.isCorrect(question, "she  has never   been there")).isTrue();
        }

        /** A tile of more than one word used to make the question impossible: the answer was split on spaces. */
        @Test
        void acceptsATileThatIsMoreThanOneWord() {
            GradableQuestion chunked = of(QuizQuestionType.REWRITE, List.of(), List.of(),
                    List.of("We went out", "in spite of", "the rain"), List.of());

            assertThat(QuizGrader.isCorrect(chunked, "We went out in spite of the rain")).isTrue();
            assertThat(QuizGrader.isCorrect(chunked, "We went out the rain in spite of")).isFalse();
        }
    }

    @Nested
    class Matching {

        private final GradableQuestion question = of(QuizQuestionType.MATCHING, List.of(), List.of(), List.of(),
                List.of("because it rained", "so we left"));

        @Test
        void requiresEveryPairToLineUp() {
            assertThat(QuizGrader.isCorrect(question, "because it rained|so we left")).isTrue();
            assertThat(QuizGrader.isCorrect(question, "so we left|because it rained")).isFalse();
        }

        /**
         * A blank half must stay a blank half. If the split dropped it, a learner who answered only the first pair
         * would be graded as though the question had one pair, and could be marked right.
         */
        @Test
        void countsAnUnansweredHalfAsWrong() {
            assertThat(QuizGrader.isCorrect(question, "because it rained|")).isFalse();
        }

        /** The separator is split literally; treated as a regex it would match between every character. */
        @Test
        void handlesClausesContainingCommas() {
            GradableQuestion commas = of(QuizQuestionType.MATCHING, List.of(), List.of(), List.of(),
                    List.of("yes, of course", "no, never"));

            assertThat(QuizGrader.isCorrect(commas, "yes, of course|no, never")).isTrue();
        }
    }

    @Nested
    class CorrectAnswerText {

        @Test
        void joinsEachTypeTheWayTheReviewScreenReadsIt() {
            assertThat(QuizGrader.correctAnswerText(
                    of(QuizQuestionType.FILL_BLANK, List.of(), List.of("has been", "'s been"), List.of(), List.of()),
                    List.of())).isEqualTo("has been / 's been");

            assertThat(QuizGrader.correctAnswerText(
                    of(QuizQuestionType.REORDER, List.of(), List.of(), List.of("She", "has", "left"), List.of()),
                    List.of())).isEqualTo("She has left");

            assertThat(QuizGrader.correctAnswerText(
                    of(QuizQuestionType.MULTIPLE_CHOICE, List.of("opt-b"), List.of(), List.of(), List.of()),
                    List.of("B. has been"))).isEqualTo("B. has been");
        }
    }
}
