package com.englow3.learning.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * The administrator's overview, counted against a real database.
 * <p>
 * The container is shared with every other integration test, so each test compares before and after rather than
 * asserting totals it cannot control.
 */
class AdminOverviewQueryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant WEEK_AGO = Instant.now().minus(7, ChronoUnit.DAYS);

    @Autowired
    private AdminOverviewQuery query;

    @Autowired
    private JdbcClient jdbc;

    private AdminOverviewQuery.ContentCounts flashcards() {
        return query.contentCounts().stream().filter(row -> row.kind().equals("FLASHCARD_SET")).findFirst()
                .orElseThrow();
    }

    @Test
    void listsEveryKindOfContent() {
        assertThat(query.contentCounts()).extracting(AdminOverviewQuery.ContentCounts::kind).containsExactly(
                "FLASHCARD_SET", "QUIZ", "DICTATION_LESSON", "SPEAKING_PROMPT", "EXAM");
    }

    @Test
    void countsDraftsAndPublishedSetsApart() {
        var before = flashcards();
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID author = fixture.learner();

        fixture.draftFlashcardSet("Draft one", author);
        fixture.publishedFlashcardSet("Live one", author);

        var after = flashcards();
        assertThat(after.drafts() - before.drafts()).isEqualTo(1);
        assertThat(after.published() - before.published()).isEqualTo(1);
        assertThat(after.pendingReview()).isEqualTo(before.pendingReview());
    }

    /** A learner who did three things this week is one active learner, not three. */
    @Test
    void countsAnActiveLearnerOnce() {
        var before = query.activitySince(WEEK_AGO);
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID learner = fixture.learner();
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID card = fixture.flashcard(set, 1, "agenda");
        UUID quiz = fixture.publishedQuiz("Tenses", learner);

        fixture.reviewLog(learner, card, set, "GOOD", Instant.now());
        fixture.reviewLog(learner, card, set, "AGAIN", Instant.now());
        fixture.quizAttempt(learner, quiz, 80, true, Instant.now());

        var after = query.activitySince(WEEK_AGO);
        assertThat(after.activeLearners() - before.activeLearners()).isEqualTo(1);
        assertThat(after.cardReviews() - before.cardReviews()).isEqualTo(2);
        assertThat(after.quizzesSubmitted() - before.quizzesSubmitted()).isEqualTo(1);
        assertThat(after.learners() - before.learners()).isEqualTo(1);
    }

    @Test
    void leavesOutActivityBeforeThePeriod() {
        var before = query.activitySince(WEEK_AGO);
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID learner = fixture.learner();
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID card = fixture.flashcard(set, 1, "brief");

        fixture.reviewLog(learner, card, set, "GOOD", WEEK_AGO.minus(3, ChronoUnit.DAYS));

        var after = query.activitySince(WEEK_AGO);
        assertThat(after.cardReviews()).isEqualTo(before.cardReviews());
        assertThat(after.activeLearners()).isEqualTo(before.activeLearners());
    }
}
