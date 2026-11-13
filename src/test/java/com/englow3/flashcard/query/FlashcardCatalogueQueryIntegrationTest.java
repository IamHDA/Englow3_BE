package com.englow3.flashcard.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.shared.error.BadRequestException;

class FlashcardCatalogueQueryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private FlashcardCatalogueQuery query;
    @Autowired
    private JdbcClient jdbc;

    @Test
    void filtersAndOrdersTheWholeCatalogueBeforePagingForTheCurrentLearner() {
        var fixture = new LearnerFixture(jdbc);
        UUID user = fixture.learner();
        UUID other = fixture.learner();
        String topic = "Topic-" + UUID.randomUUID();
        UUID first = fixture.publishedFlashcardSet("First", user);
        UUID second = fixture.publishedFlashcardSet("Second", user);
        UUID draft = fixture.draftFlashcardSet("Hidden", user);
        for (UUID id : java.util.List.of(first, second, draft)) {
            jdbc.sql("update flashcard_sets set topic = :topic where id = :id").param("topic", topic).param("id", id)
                    .update();
        }
        jdbc.sql("update flashcard_sets set description = 'Description-only keyword' where id = :id")
                .param("id", second).update();
        Instant now = Instant.now();
        var card1 = fixture.flashcard(first, 1, "one");
        var card2 = fixture.flashcard(first, 2, "two");
        var card3 = fixture.flashcard(second, 1, "three");
        fixture.review(user, card1, "LEARNING", now.minusSeconds(60));
        fixture.review(user, card2, "REVIEW", now.minusSeconds(60));
        fixture.review(user, card3, "MASTERED", now.plusSeconds(3600));
        fixture.review(other, card1, "MASTERED", now.plusSeconds(3600));

        var due = query.search(user, now, topic.toLowerCase(), null, "due", PageRequest.of(0, 1));
        assertThat(due.getTotalElements()).isEqualTo(2);
        assertThat(due.getContent()).singleElement().satisfies(set -> {
            assertThat(set.id()).isEqualTo(first);
            assertThat(set.dueCount()).isEqualTo(2);
        });
        assertThat(query.search(user, now, topic, null, "due", PageRequest.of(1, 1)).getContent()).singleElement()
                .satisfies(set -> assertThat(set.id()).isEqualTo(second));
        assertThat(query.search(user, now, topic, null, "progress", PageRequest.of(0, 1)).getContent()).singleElement()
                .satisfies(set -> assertThat(set.id()).isEqualTo(second));
        assertThat(query.search(user, now, topic, "KEYWORD", "cards", PageRequest.of(0, 30)).getContent())
                .singleElement().satisfies(set -> assertThat(set.id()).isEqualTo(second));
        assertThat(query.information(user, now).totalDueCount()).isEqualTo(2);
        assertThat(query.information(user, now).topics()).contains(topic);
        assertThatThrownBy(() -> query.search(user, now, null, null, "raw_sql", PageRequest.of(0, 20)))
                .isInstanceOf(BadRequestException.class);
    }
}
