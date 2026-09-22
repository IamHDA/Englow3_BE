package com.englow3.learning.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.shared.persistence.SqlTime;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * The read model behind the daily path, against a real database.
 * <p>
 * This is the query with the most to get wrong: four activity tables unioned for the streak, a cross-module read into
 * {@code exam_attempts}, and several aggregates that divide. None of it is reachable by a unit test - a mocked
 * repository returns whatever it is told, so a join that drops rows looks identical to one that does not.
 */
class DailyPathQueryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-03-15T10:00:00Z");
    private static final Instant EPOCH = Instant.EPOCH;

    @Autowired
    private DailyPathQuery query;

    @Autowired
    private JdbcClient jdbc;

    private LearnerFixture fixture;
    private UUID learner;
    private UUID other;

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
        learner = fixture.learner();
        other = fixture.learner();
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    @Nested
    class StudyDays {

        /** One row per day, however many things were done on it - three sessions on Tuesday is still one day. */
        @Test
        void countsADayOnceHoweverMuchWasDoneOnIt() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            UUID card = fixture.flashcard(set, 1, "agenda");
            fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
            fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1).plusSeconds(60));

            assertThat(query.studyDays(learner, EPOCH)).hasSize(1);
        }

        /** All four kinds count. A streak that ignored one would tell a learner a day they studied did not happen. */
        @Test
        void countsEveryKindOfPractice() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            UUID card = fixture.flashcard(set, 1, "agenda");
            UUID lesson = fixture.publishedDictationLesson("Airport", learner);
            UUID sentence = fixture.dictationSentence(lesson, 1, "The cat sat.");
            UUID quiz = fixture.publishedQuiz("Tenses", learner);

            fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
            fixture.dictationAttempt(learner, lesson, sentence, "The cat sat.", new BigDecimal("100.00"), daysAgo(2));
            fixture.quizAttempt(learner, quiz, 80, true, daysAgo(3));
            examAttempt(learner, "SCORED", daysAgo(4));

            assertThat(query.studyDays(learner, EPOCH)).hasSize(4);
        }

        /**
         * The cross-module read, and the reason it is allowed. A learner who spent two hours on a mock paper studied
         * that day; a streak counting only this module's tables would tell them they did not.
         */
        @Test
        void countsADayWhoseOnlyWorkWasAnExam() {
            examAttempt(learner, "SCORED", daysAgo(1));

            assertThat(query.studyDays(learner, EPOCH))
                    .containsExactly(LocalDate.ofInstant(daysAgo(1), ZoneOffset.UTC));
        }

        /**
         * An unfinished paper is not a finished one. Counting it would let an opened-and-abandoned tab be a study day.
         */
        @Test
        void doesNotCountAnExamStillInProgress() {
            examAttempt(learner, "IN_PROGRESS", daysAgo(1));

            assertThat(query.studyDays(learner, EPOCH)).isEmpty();
        }

        @Test
        void doesNotCountSomebodyElsesWork() {
            UUID set = fixture.publishedFlashcardSet("Core", other);
            UUID card = fixture.flashcard(set, 1, "agenda");
            fixture.reviewLog(other, card, set, "GOOD", daysAgo(1));

            assertThat(query.studyDays(learner, EPOCH)).isEmpty();
        }

        @Test
        void readsNewestFirst() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            UUID card = fixture.flashcard(set, 1, "agenda");
            fixture.reviewLog(learner, card, set, "GOOD", daysAgo(5));
            fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));

            assertThat(query.studyDays(learner, EPOCH)).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }
    }

    @Nested
    class ActivityTotals {

        /** What the experience counter is derived from. Every unit counted once, from four separate tables. */
        @Test
        void countsEachKindOfWorkSeparately() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            UUID card = fixture.flashcard(set, 1, "agenda");
            UUID lesson = fixture.publishedDictationLesson("Airport", learner);
            UUID sentence = fixture.dictationSentence(lesson, 1, "The cat sat.");
            UUID quiz = fixture.publishedQuiz("Tenses", learner);

            fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
            fixture.reviewLog(learner, card, set, "AGAIN", daysAgo(1));
            fixture.dictationAttempt(learner, lesson, sentence, "x", new BigDecimal("50.00"), daysAgo(1));
            fixture.quizAttempt(learner, quiz, 80, true, daysAgo(1));
            examAttempt(learner, "SCORED", daysAgo(1));

            var totals = query.activityTotals(learner, EPOCH);

            assertThat(totals.flashcardReviews()).isEqualTo(2);
            assertThat(totals.dictationSentences()).isEqualTo(1);
            assertThat(totals.quizAttempts()).isEqualTo(1);
            assertThat(totals.examAttempts()).isEqualTo(1);
        }

        /** A learner who has done nothing gets zeros, not an empty result the caller has to handle. */
        @Test
        void answersZeroForALearnerWhoHasDoneNothing() {
            var totals = query.activityTotals(learner, EPOCH);

            assertThat(totals.flashcardReviews()).isZero();
            assertThat(totals.examAttempts()).isZero();
        }
    }

    @Nested
    class DueCards {

        @Test
        void countsOnlyCardsThatHaveComeDue() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            fixture.review(learner, fixture.flashcard(set, 1, "agenda"), "LEARNING", daysAgo(1));
            fixture.review(learner, fixture.flashcard(set, 2, "brief"), "LEARNING", NOW.plusSeconds(3_600));

            assertThat(query.cardsDue(learner, NOW)).isEqualTo(1);
        }

        /** A set with nothing due does not appear at all - the roadmap lists work, not everything owned. */
        @Test
        void leavesOutASetWithNothingDue() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            fixture.review(learner, fixture.flashcard(set, 1, "agenda"), "LEARNING", NOW.plusSeconds(3_600));

            assertThat(query.dueSets(learner, NOW, 3)).isEmpty();
        }

        /**
         * A card from an unpublished set is not practisable, so it must not appear as work. Without the status filter
         * an admin's draft would show up on their own roadmap.
         */
        @Test
        void leavesOutASetThatIsNotPublished() {
            UUID set = fixture.draftFlashcardSet("Draft", learner);
            fixture.review(learner, fixture.flashcard(set, 1, "agenda"), "LEARNING", daysAgo(1));

            assertThat(query.dueSets(learner, NOW, 3)).isEmpty();
        }

        @Test
        void reportsHowFarThroughTheSetTheLearnerIs() {
            UUID set = fixture.publishedFlashcardSet("Core", learner);
            fixture.review(learner, fixture.flashcard(set, 1, "agenda"), "MASTERED", daysAgo(1));
            fixture.review(learner, fixture.flashcard(set, 2, "brief"), "LEARNING", daysAgo(1));
            fixture.review(learner, fixture.flashcard(set, 3, "concur"), "LEARNING", daysAgo(1));
            fixture.review(learner, fixture.flashcard(set, 4, "draft"), "LEARNING", daysAgo(1));

            var due = query.dueSets(learner, NOW, 3);

            assertThat(due).singleElement().satisfies(set1 -> {
                assertThat(set1.dueCount()).isEqualTo(4);
                assertThat(set1.completionPercent()).isEqualTo(25);
            });
        }

        @Test
        void putsTheSetWithTheMostOwedFirst() {
            UUID small = fixture.publishedFlashcardSet("Small", learner);
            UUID big = fixture.publishedFlashcardSet("Big", learner);
            fixture.review(learner, fixture.flashcard(small, 1, "agenda"), "LEARNING", daysAgo(1));
            fixture.review(learner, fixture.flashcard(big, 1, "brief"), "LEARNING", daysAgo(1));
            fixture.review(learner, fixture.flashcard(big, 2, "concur"), "LEARNING", daysAgo(1));

            assertThat(query.dueSets(learner, NOW, 3)).extracting(DailyPathQuery.DueSet::name).containsExactly("Big",
                    "Small");
        }
    }

    @Nested
    class Quizzes {

        /** The pass mark is per quiz, so passing is read from the attempt rather than compared to one global number. */
        @Test
        void countsOnlyAttemptsThatPassed() {
            UUID quiz = fixture.publishedQuiz("Tenses", learner);
            fixture.quizAttempt(learner, quiz, 80, true, daysAgo(1));
            fixture.quizAttempt(learner, quiz, 40, false, daysAgo(1));

            assertThat(query.quizzesPassedSince(learner, EPOCH)).isEqualTo(1);
        }

        /** A quiz already passed is not outstanding work, whatever else the learner did on it. */
        @Test
        void leavesOutAQuizTheLearnerHasPassed() {
            UUID passed = fixture.publishedQuiz("Passed", learner);
            UUID failed = fixture.publishedQuiz("Failed", learner);
            fixture.quizAttempt(learner, passed, 80, true, daysAgo(1));
            fixture.quizAttempt(learner, failed, 40, false, daysAgo(1));

            assertThat(query.unpassedQuizzes(learner, 50)).extracting(DailyPathQuery.PendingQuiz::quizId)
                    .contains(failed).doesNotContain(passed);
        }

        /** Never attempted is also unpassed, and the best score is null rather than zero - nothing was measured. */
        @Test
        void showsNoScoreForAQuizNobodyHasTried() {
            UUID untried = fixture.publishedQuiz("Untried", learner);

            // A generous limit, because quizzes are content and every test in this class shares the database. The
            // assertion is about this quiz, not about where it lands in a list the rest of the suite also writes to.
            assertThat(query.unpassedQuizzes(learner, 100)).filteredOn(quiz -> quiz.quizId().equals(untried))
                    .singleElement().satisfies(quiz -> assertThat(quiz.bestScorePercent()).isNull());
        }

        /** The limit is what keeps the roadmap a plan rather than a backlog, so it has to actually bite. */
        @Test
        void handsBackNoMoreThanAsked() {
            for (int i = 0; i < 5; i++) {
                fixture.publishedQuiz("Limit " + i, learner);
            }

            assertThat(query.unpassedQuizzes(learner, 3)).hasSize(3);
        }
    }

    @Nested
    class Dictation {

        /** A lesson is unfinished while any sentence is below the threshold. */
        @Test
        void countsTheSentencesStillShortOfTheThreshold() {
            UUID lesson = fixture.publishedDictationLesson("Airport", learner);
            UUID cleared = fixture.dictationSentence(lesson, 1, "One.");
            UUID missed = fixture.dictationSentence(lesson, 2, "Two.");
            fixture.dictationSentence(lesson, 3, "Three.");
            fixture.dictationAttempt(learner, lesson, cleared, "One.", new BigDecimal("100.00"), daysAgo(1));
            fixture.dictationAttempt(learner, lesson, missed, "wrong", new BigDecimal("20.00"), daysAgo(1));

            assertThat(query.unfinishedLessons(learner, new BigDecimal("80"), 50))
                    .filteredOn(pending -> pending.lessonId().equals(lesson)).singleElement()
                    .satisfies(pending -> assertThat(pending.remainingSentences()).isEqualTo(2));
        }

        /** Best attempt counts, not latest: a learner who got it right once has done that line. */
        @Test
        void readsTheBestAttemptNotTheLast() {
            UUID lesson = fixture.publishedDictationLesson("Airport", learner);
            UUID sentence = fixture.dictationSentence(lesson, 1, "One.");
            fixture.dictationAttempt(learner, lesson, sentence, "One.", new BigDecimal("100.00"), daysAgo(2));
            fixture.dictationAttempt(learner, lesson, sentence, "wrong", new BigDecimal("10.00"), daysAgo(1));

            assertThat(query.unfinishedLessons(learner, new BigDecimal("80"), 50))
                    .noneMatch(pending -> pending.lessonId().equals(lesson));
        }
    }

    /** Written here rather than in the fixture: only this test needs an exam, and it needs the barest possible one. */
    private void examAttempt(UUID userId, String status, Instant submittedAt) {
        UUID examId = UUID.randomUUID();
        jdbc.sql("""
                insert into exams (id, title, description, exam_type, target_level, duration_seconds,
                                   max_raw_score, pass_score, status, version_number, created_by_user_id)
                values (:id, 'Mock paper', '', 'MOCK', 'B1', 3600, 200, 100, 'PUBLISHED', 1, :authorId)
                """).param("id", examId).param("authorId", userId).update();

        jdbc.sql("""
                insert into exam_attempts (id, exam_id, exam_version_number, user_id, status, started_at,
                                           expires_at, submitted_at, max_raw_score, question_count)
                values (:id, :examId, 1, :userId, :status, :startedAt, :expiresAt, :submittedAt, 200, 100)
                """).param("id", UUID.randomUUID()).param("examId", examId).param("userId", userId)
                .param("status", status).param("startedAt", SqlTime.at(submittedAt))
                .param("expiresAt", SqlTime.at(submittedAt.plusSeconds(3_600)))
                .param("submittedAt", "SCORED".equals(status) ? SqlTime.at(submittedAt) : null).update();
    }
}
