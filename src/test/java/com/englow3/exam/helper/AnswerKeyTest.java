package com.englow3.exam.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.exam.entity.QuestionType;

class AnswerKeyTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Nested
    class SingleChoice {

        private final AnswerKey key = new AnswerKey(QuestionType.SINGLE_CHOICE, BigDecimal.ONE, Set.of(a), List.of());

        @Test
        void theCorrectOptionEarnsTheWholeQuestion() {
            assertThat(key.grade(Set.of(a), null)).isEqualTo(new AnswerKey.Grade(true, BigDecimal.ONE));
        }

        @Test
        void anotherOptionEarnsNothing() {
            assertThat(key.grade(Set.of(b), null).awarded()).isEqualByComparingTo("0");
        }

        @Test
        void noAnswerEarnsNothing() {
            assertThat(key.grade(Set.of(), null).correct()).isFalse();
        }

        @Test
        void takesOneNumberAndOneChoice() {
            assertThat(key.numberSpan()).isEqualTo(1);
            assertThat(key.choiceLimit()).isEqualTo(1);
        }
    }

    /** "Choose TWO letters": a point per correct letter, in any order (D-IELTS-3). */
    @Nested
    class MultipleChoice {

        private final AnswerKey key = new AnswerKey(QuestionType.MULTIPLE_CHOICE, new BigDecimal("2"), Set.of(a, b),
                List.of());

        @Test
        void bothCorrectLettersEarnTheWholeQuestion() {
            assertThat(key.grade(Set.of(b, a), null)).satisfies(grade -> {
                assertThat(grade.correct()).isTrue();
                assertThat(grade.awarded()).isEqualByComparingTo("2");
            });
        }

        @Test
        void oneCorrectLetterEarnsItsShareButIsNotCorrect() {
            assertThat(key.grade(Set.of(a, c), null)).satisfies(grade -> {
                assertThat(grade.correct()).isFalse();
                assertThat(grade.awarded()).isEqualByComparingTo("1");
            });
        }

        @Test
        void oneCorrectLetterAloneStillEarnsItsShare() {
            assertThat(key.grade(Set.of(b), null).awarded()).isEqualByComparingTo("1");
        }

        @Test
        void choosingMoreLettersThanAskedEarnsNothing() {
            assertThat(key.grade(Set.of(a, b, c), null).awarded()).isEqualByComparingTo("0");
        }

        @Test
        void onlyWrongLettersEarnNothing() {
            assertThat(key.grade(Set.of(c, d), null).awarded()).isEqualByComparingTo("0");
        }

        @Test
        void takesTwoNumbersAndAllowsTwoChoices() {
            assertThat(key.numberSpan()).isEqualTo(2);
            assertThat(key.choiceLimit()).isEqualTo(2);
        }
    }

    /** Only case and whitespace are forgiven; every other variant is an accepted answer of its own (D-IELTS-4). */
    @Nested
    class TextEntry {

        private final AnswerKey key = new AnswerKey(QuestionType.TEXT_ENTRY, BigDecimal.ONE, Set.of(),
                List.of(new AnswerKey.Accepted("the museum", false), new AnswerKey.Accepted("museum", false)));

        @Test
        void anAcceptedSpellingEarnsTheWholeQuestion() {
            assertThat(key.grade(Set.of(), "museum")).isEqualTo(new AnswerKey.Grade(true, BigDecimal.ONE));
        }

        @Test
        void caseIsIgnored() {
            assertThat(key.grade(Set.of(), "The Museum").correct()).isTrue();
        }

        @Test
        void extraWhitespaceIsIgnored() {
            assertThat(key.grade(Set.of(), "  the   museum ").correct()).isTrue();
        }

        @Test
        void aMisspellingIsWrong() {
            assertThat(key.grade(Set.of(), "musuem").correct()).isFalse();
        }

        @Test
        void aVariantNobodyListedIsWrong() {
            assertThat(key.grade(Set.of(), "a museum").correct()).isFalse();
        }

        @Test
        void aBlankAnswerIsWrong() {
            assertThat(key.grade(Set.of(), "   ").correct()).isFalse();
            assertThat(key.grade(Set.of(), null).correct()).isFalse();
        }

        @Test
        void aCaseSensitiveAnswerMustMatchItsCase() {
            AnswerKey strict = new AnswerKey(QuestionType.TEXT_ENTRY, BigDecimal.ONE, Set.of(),
                    List.of(new AnswerKey.Accepted("DNA", true)));

            assertThat(strict.grade(Set.of(), "DNA").correct()).isTrue();
            assertThat(strict.grade(Set.of(), "dna").correct()).isFalse();
        }
    }

    /** Picking one entry of the set's shared list: right or wrong, like a single choice. */
    @Nested
    class Matching {

        private final AnswerKey key = new AnswerKey(QuestionType.MATCHING, BigDecimal.ONE, Set.of(c), List.of());

        @Test
        void theEntryThatAnswersTheQuestionEarnsIt() {
            assertThat(key.grade(Set.of(c), null).correct()).isTrue();
        }

        @Test
        void anotherEntryOfTheListEarnsNothing() {
            assertThat(key.grade(Set.of(a), null).awarded()).isEqualByComparingTo("0");
        }

        @Test
        void takesOneNumberAndOneChoice() {
            assertThat(key.numberSpan()).isEqualTo(1);
            assertThat(key.choiceLimit()).isEqualTo(1);
        }
    }
}
