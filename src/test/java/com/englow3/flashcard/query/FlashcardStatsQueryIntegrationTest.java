package com.englow3.flashcard.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class FlashcardStatsQueryIntegrationTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-03-15T10:00:00Z");
    private static final Instant EPOCH = Instant.EPOCH;

    @Autowired
    private FlashcardStatsQuery flashcardStats;

    @Autowired
    private JdbcClient jdbc;

    private LearnerFixture fixture;
    private UUID learner;

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
        learner = fixture.learner();
    }

    private static Instant daysAgo(int days) {
        return NOW.minus(days, ChronoUnit.DAYS);
    }

    /**
     * Distinct cards, not answers. Answering the same card four times in a sitting is one card studied - counting the
     * answers would let a learner drilling one hard word report a day's worth of vocabulary.
     */
    @Test
    void countsACardOnceHoweverOftenItWasAnswered() {
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID card = fixture.flashcard(set, 1, "agenda");
        UUID second = fixture.flashcard(set, 2, "brief");
        fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
        fixture.reviewLog(learner, card, set, "AGAIN", daysAgo(2));
        fixture.reviewLog(learner, second, set, "GOOD", daysAgo(1));

        assertThat(flashcardStats.periodSummary(learner, EPOCH).cardsStudied()).isEqualTo(2);
    }

    /** The window is a window: work older than it belongs to a different period's figures. */
    @Test
    void leavesOutWorkFromBeforeTheWindow() {
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID card = fixture.flashcard(set, 1, "agenda");
        fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
        fixture.reviewLog(learner, card, set, "GOOD", daysAgo(40));

        assertThat(flashcardStats.periodSummary(learner, daysAgo(7)).cardsStudied()).isEqualTo(1);
    }

    /** Anything but AGAIN counts as recalled - the question is whether they knew it, not how comfortably. */
    @Test
    void readsRetentionAsTheShareThatWasNotForgotten() {
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID card = fixture.flashcard(set, 1, "agenda");
        fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
        fixture.reviewLog(learner, card, set, "EASY", daysAgo(1));
        fixture.reviewLog(learner, card, set, "HARD", daysAgo(1));
        fixture.reviewLog(learner, card, set, "AGAIN", daysAgo(1));

        assertThat(flashcardStats.periodSummary(learner, EPOCH).retentionPercent()).isEqualTo(75);
    }

    /**
     * The division that would otherwise fail. A learner who has answered nothing has no retention to report, and zero
     * is the honest answer only because the screen shows it as "no data" rather than as "you forgot everything".
     */
    @Test
    void doesNotDivideByZeroForALearnerWhoHasAnsweredNothing() {
        assertThat(flashcardStats.periodSummary(learner, EPOCH).retentionPercent()).isZero();
        assertThat(flashcardStats.periodSummary(learner, EPOCH).cardsStudied()).isZero();
        assertThat(flashcardStats.periodSummary(learner, EPOCH).studySeconds()).isZero();
    }

    @Test
    void groupsActivityByDay() {
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID card = fixture.flashcard(set, 1, "agenda");
        fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1));
        fixture.reviewLog(learner, card, set, "GOOD", daysAgo(1).plusSeconds(120));
        fixture.reviewLog(learner, card, set, "AGAIN", daysAgo(3));

        assertThat(flashcardStats.activityByDay(learner, EPOCH)).hasSize(2);
    }

    /**
     * Read from the schedule's lapse count, not from the answer log. A card is difficult because the scheduler has had
     * to reset it, which is a different claim from "one answer went badly" - and a card still being learned is just
     * new, not hard.
     */
    @Test
    void putsTheMostForgottenCardFirstAndLeavesOutOnesNeverLapsed() {
        UUID set = fixture.publishedFlashcardSet("Core", learner);
        UUID never = fixture.flashcard(set, 1, "easy");
        UUID once = fixture.flashcard(set, 2, "medium");
        UUID often = fixture.flashcard(set, 3, "hard");
        fixture.review(learner, never, "REVIEWING", daysAgo(1), 0);
        fixture.review(learner, once, "REVIEWING", daysAgo(1), 1);
        fixture.review(learner, often, "REVIEWING", daysAgo(1), 4);

        assertThat(flashcardStats.difficultCards(learner, 5)).extracting(FlashcardStatsQuery.DifficultCard::lemma)
                .containsExactly("hard", "medium");
    }

    @Test
    void keepsOneLearnersFiguresOutOfAnothers() {
        UUID other = fixture.learner();
        UUID set = fixture.publishedFlashcardSet("Core", other);
        UUID card = fixture.flashcard(set, 1, "agenda");
        fixture.reviewLog(other, card, set, "GOOD", daysAgo(1));

        assertThat(flashcardStats.periodSummary(learner, EPOCH).cardsStudied()).isZero();
    }
}
