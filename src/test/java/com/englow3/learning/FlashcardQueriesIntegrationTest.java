package com.englow3.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.learning.entity.Flashcard;
import com.englow3.learning.repository.FlashcardRepository;
import com.englow3.learning.repository.FlashcardReviewRepository;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * The flashcard queries that answer for a page or a session in one go instead of one card or one set at a time. The set
 * list's batched due and mastered counts have to agree with the per-set counts the set's own page still uses, or a set
 * would show one number in the list and another once opened.
 */
class FlashcardQueriesIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private FlashcardReviewRepository reviews;

    @Autowired
    private FlashcardRepository cards;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void countsDueAndMasteredPerSetAsTheSetPageDoes() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID learner = fixture.learner();
        UUID other = fixture.learner();
        Instant now = Instant.now();
        Instant past = now.minus(Duration.ofDays(1));
        Instant future = now.plus(Duration.ofDays(3));

        UUID firstSet = fixture.publishedFlashcardSet("First", learner);
        UUID due = fixture.flashcard(firstSet, 1, "acquire");
        UUID mastered = fixture.flashcard(firstSet, 2, "borrow");
        UUID masteredAndDue = fixture.flashcard(firstSet, 3, "carry");
        fixture.review(learner, due, "LEARNING", past);
        fixture.review(learner, mastered, "MASTERED", future);
        fixture.review(learner, masteredAndDue, "MASTERED", past);
        // Someone else's progress is not this learner's.
        fixture.review(other, due, "MASTERED", past);

        UUID secondSet = fixture.publishedFlashcardSet("Second", learner);
        fixture.review(learner, fixture.flashcard(secondSet, 1, "deliver"), "LEARNING", future);

        UUID untouchedSet = fixture.publishedFlashcardSet("Untouched", learner);
        fixture.flashcard(untouchedSet, 1, "earn");

        Map<UUID, long[]> counts = reviews.countDueAndMasteredBySet(learner, List.of(firstSet, secondSet, untouchedSet),
                now);

        for (UUID setId : List.of(firstSet, secondSet)) {
            assertThat(counts.get(setId)).containsExactly(reviews.countDueInSet(learner, setId, now),
                    reviews.countMasteredInSet(learner, setId));
        }
        assertThat(counts.get(firstSet)).containsExactly(2, 2);
        assertThat(counts.get(secondSet)).containsExactly(0, 0);
        assertThat(counts).doesNotContainKey(untouchedSet);
    }

    @Test
    void asksNothingForAnEmptyPage() {
        assertThat(reviews.countDueAndMasteredBySet(UUID.randomUUID(), List.of(), Instant.now())).isEmpty();
    }

    /**
     * The study queue asks only for the cards a session takes: due ones in the set's order, then ones this learner has
     * never seen. Loading the whole set to pick them out was a quarter of a megabyte for a set of four hundred.
     */
    @Test
    void picksTheSessionsCardsWithoutLoadingTheWholeSet() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID learner = fixture.learner();
        UUID other = fixture.learner();
        Instant now = Instant.now();
        UUID setId = fixture.publishedFlashcardSet("Queue", learner);
        UUID dueFirst = fixture.flashcard(setId, 1, "acquire");
        UUID notDue = fixture.flashcard(setId, 2, "borrow");
        UUID unseen = fixture.flashcard(setId, 3, "carry");
        UUID dueLater = fixture.flashcard(setId, 4, "deliver");
        UUID seenByOthersOnly = fixture.flashcard(setId, 5, "earn");
        fixture.review(learner, dueFirst, "LEARNING", now.minus(Duration.ofDays(1)));
        fixture.review(learner, notDue, "REVIEW", now.plus(Duration.ofDays(2)));
        fixture.review(learner, dueLater, "LEARNING", now.minus(Duration.ofDays(5)));
        fixture.review(other, seenByOthersOnly, "MASTERED", now.minus(Duration.ofDays(1)));

        List<UUID> due = reviews.findDueCardsInSet(learner, setId, now, Limit.of(10)).stream()
                .map(row -> ((Flashcard) row[0]).getId()).toList();
        List<UUID> neverSeen = cards.findUnseenInSet(learner, setId, Limit.of(10)).stream().map(Flashcard::getId)
                .toList();

        assertThat(due).containsExactly(dueFirst, dueLater);
        assertThat(neverSeen).containsExactly(unseen, seenByOthersOnly);
        assertThat(cards.findUnseenInSet(learner, setId, Limit.of(1))).extracting(Flashcard::getId)
                .containsExactly(unseen);
    }
}
