package com.englow3.flashcard.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.flashcard.dto.command.FlashcardSetScope;
import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
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

        var due = query.search(user, Set.of(), Set.of(), now, FlashcardSetScope.CATALOGUE, topic.toLowerCase(), null,
                "due", PageRequest.of(0, 1));
        assertThat(due.getTotalElements()).isEqualTo(2);
        assertThat(due.getContent()).singleElement().satisfies(set -> {
            assertThat(set.id()).isEqualTo(first);
            assertThat(set.dueCount()).isEqualTo(2);
        });
        assertThat(query.search(user, Set.of(), Set.of(), now, FlashcardSetScope.CATALOGUE, topic, null, "due",
                PageRequest.of(1, 1)).getContent()).singleElement()
                        .satisfies(set -> assertThat(set.id()).isEqualTo(second));
        assertThat(query.search(user, Set.of(), Set.of(), now, FlashcardSetScope.CATALOGUE, topic, null, "progress",
                PageRequest.of(0, 1)).getContent()).singleElement()
                        .satisfies(set -> assertThat(set.id()).isEqualTo(second));
        assertThat(query.search(user, Set.of(), Set.of(), now, FlashcardSetScope.CATALOGUE, topic, "KEYWORD", "cards",
                PageRequest.of(0, 30)).getContent()).singleElement()
                        .satisfies(set -> assertThat(set.id()).isEqualTo(second));
        assertThat(query.information(user, Set.of(), Set.of(), now).totalDueCount()).isEqualTo(2);
        assertThat(query.information(user, Set.of(), Set.of(), now).topics()).contains(topic);
        assertThatThrownBy(() -> query.search(user, Set.of(), Set.of(), now, FlashcardSetScope.CATALOGUE, null, null,
                "raw_sql", PageRequest.of(0, 20))).isInstanceOf(BadRequestException.class);
    }

    /**
     * The SQL half of {@code FlashcardSet.isVisibleTo}: a personal set never reaches the catalogue, and the community
     * shelf holds what others made public plus what a friend chose to share with this learner - while they are still
     * friends, and never from someone either side has blocked.
     */
    @Test
    void personalSetsReachOnlyTheShelvesTheirVisibilityAllows() {
        var fixture = new LearnerFixture(jdbc);
        UUID me = fixture.learner();
        UUID friend = fixture.learner();
        UUID stranger = fixture.learner();
        UUID blocker = fixture.learner();
        UUID mine = personalSet(fixture, "Mine", me, "PRIVATE");
        UUID strangerPublic = personalSet(fixture, "Stranger public", stranger, "PUBLIC");
        UUID strangerPrivate = personalSet(fixture, "Stranger private", stranger, "PRIVATE");
        UUID sharedWithMe = personalSet(fixture, "Shared with me", friend, "SELECTED_FRIENDS");
        UUID sharedWithOthers = personalSet(fixture, "Shared with others", friend, "SELECTED_FRIENDS");
        UUID blockerPublic = personalSet(fixture, "Blocker public", blocker, "PUBLIC");
        share(sharedWithMe, me);
        share(sharedWithOthers, stranger);
        UUID catalogue = fixture.publishedFlashcardSet("Catalogue", stranger);
        Instant now = Instant.now();

        assertThat(ids(query.search(me, Set.of(), Set.of(), now, FlashcardSetScope.CATALOGUE, null, null, "due",
                PageRequest.of(0, 100)))).contains(catalogue).doesNotContain(mine, strangerPublic, sharedWithMe);
        assertThat(ids(query.search(me, Set.of(), Set.of(), now, FlashcardSetScope.MINE, null, null, "recent",
                PageRequest.of(0, 100)))).containsExactly(mine);
        assertThat(ids(query.search(me, Set.of(friend), Set.of(blocker), now, FlashcardSetScope.COMMUNITY, null, null,
                "recent", PageRequest.of(0, 100)))).contains(strangerPublic, sharedWithMe).doesNotContain(mine,
                        strangerPrivate, sharedWithOthers, blockerPublic, catalogue);
        // Unfriended: the share row stays, but grants nothing.
        assertThat(ids(query.search(me, Set.of(), Set.of(), now, FlashcardSetScope.COMMUNITY, null, null, "recent",
                PageRequest.of(0, 100)))).doesNotContain(sharedWithMe);
    }

    private UUID personalSet(LearnerFixture fixture, String name, UUID owner, String visibility) {
        UUID id = fixture.publishedFlashcardSet(name, owner);
        jdbc.sql("update flashcard_sets set kind = 'PERSONAL', visibility = :v where id = :id").param("v", visibility)
                .param("id", id).update();
        return id;
    }

    private void share(UUID setId, UUID userId) {
        jdbc.sql("insert into flashcard_set_shares (flashcard_set_id, user_id) values (:s, :u)").param("s", setId)
                .param("u", userId).update();
    }

    private static java.util.List<UUID> ids(org.springframework.data.domain.Page<FlashcardSetSummaryResult> page) {
        return page.getContent().stream().map(FlashcardSetSummaryResult::id).toList();
    }
}
