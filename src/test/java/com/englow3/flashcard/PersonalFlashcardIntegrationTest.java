package com.englow3.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.flashcard.dto.command.FlashcardSetScope;
import com.englow3.flashcard.dto.command.PersonalCardCommand;
import com.englow3.flashcard.dto.command.RateFlashcardCommand;
import com.englow3.flashcard.dto.command.SavePersonalSetCommand;
import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
import com.englow3.flashcard.dto.result.PersonalSetResult;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import com.englow3.flashcard.entity.ReviewRating;
import com.englow3.flashcard.service.FlashcardService;
import com.englow3.flashcard.service.PersonalFlashcardService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * A learner's own set end to end against the real schema: the mapping of the new columns and the share table, a friend
 * reaching the set through the real friendship tables, an unfriending taking it away again, and deletion cascading over
 * everyone's progress.
 */
class PersonalFlashcardIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    PersonalFlashcardService personalFlashcardService;
    @Autowired
    FlashcardService flashcardService;

    private LearnerFixture fixture;
    private UUID owner;
    private UUID friend;
    private UUID stranger;

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
        owner = fixture.learner("Owner");
        friend = fixture.learner("Friend");
        stranger = fixture.learner("Stranger");
        jdbc.sql("""
                insert into friendships (id, requester_id, addressee_id, status, accepted_at)
                values (:id, :a, :b, 'ACCEPTED', now())
                """).param("id", UUID.randomUUID()).param("a", owner).param("b", friend).update();
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void aSetSharedWithAFriendIsStudiedByThemAndHiddenFromEveryoneElse() {
        SignedIn.as(jdbc, owner);
        PersonalSetResult created = personalFlashcardService.createSet(
                new SavePersonalSetCommand("Travel", null, "travel", "A2", FlashcardSetVisibility.SELECTED_FRIENDS,
                        List.of(friend), List.of(card("ticket", "vé"), card("plane", "máy bay")), null));

        assertThat(created.sharedWith()).extracting(PersonalSetResult.Person::displayName).containsExactly("Friend");
        assertThat(created.cards()).extracting(c -> c.orderNo()).containsExactly(1, 2);
        var mine = flashcardService.searchSets(FlashcardSetScope.MINE, null, null, "recent", PageRequest.of(0, 50));
        assertThat(ids(mine)).containsExactly(created.id());
        assertThat(mine.getContent().getFirst().canEdit()).isTrue();
        assertThat(flashcardService.setSummary(created.id()).canEdit()).isTrue();

        SignedIn.as(jdbc, friend);
        var shelf = flashcardService.searchSets(FlashcardSetScope.COMMUNITY, null, null, "recent",
                PageRequest.of(0, 50));
        assertThat(shelf.getContent()).filteredOn(set -> set.id().equals(created.id())).singleElement()
                .satisfies(set -> {
                    assertThat(set.owner().displayName()).isEqualTo("Owner");
                    assertThat(set.canEdit()).isFalse();
                });
        UUID firstCard = created.cards().getFirst().id();
        flashcardService.rate(new RateFlashcardCommand(firstCard, ReviewRating.GOOD, 4));

        SignedIn.as(jdbc, stranger);
        assertThatThrownBy(() -> flashcardService.setSummary(created.id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> flashcardService.rate(new RateFlashcardCommand(firstCard, ReviewRating.GOOD, 4)))
                .isInstanceOf(NotFoundException.class);

        // Unfriended: the share row is still there, and grants nothing.
        jdbc.sql("delete from friendships where requester_id = :a and addressee_id = :b").param("a", owner)
                .param("b", friend).update();
        SignedIn.as(jdbc, friend);
        assertThatThrownBy(() -> flashcardService.setSummary(created.id())).isInstanceOf(NotFoundException.class);

        // Deleting takes the cards, the share and the friend's progress with it.
        SignedIn.as(jdbc, owner);
        personalFlashcardService.deleteSet(created.id());
        assertThat(count("flashcards where flashcard_set_id = :id", created.id())).isZero();
        assertThat(count("flashcard_set_shares where flashcard_set_id = :id", created.id())).isZero();
        assertThat(count("flashcard_reviews where flashcard_id = :id", firstCard)).isZero();
    }

    @Test
    void editingKeepsCardIdsAndProgressAndMovesTheVersion() {
        SignedIn.as(jdbc, owner);
        PersonalSetResult created = personalFlashcardService.createSet(new SavePersonalSetCommand("Words", null,
                "general", null, FlashcardSetVisibility.PRIVATE, List.of(), List.of(card("ticket", "vé")), null));
        UUID cardId = created.cards().getFirst().id();
        flashcardService.rate(new RateFlashcardCommand(cardId, ReviewRating.GOOD, 4));

        var revisedCard = personalFlashcardService.updateCard(created.id(), cardId, card("ticket", "tấm vé"));
        var added = personalFlashcardService.addCard(created.id(), card("gate", "cổng"));
        PersonalSetResult revised = personalFlashcardService.updateSet(created.id(),
                new SavePersonalSetCommand("Airport", "", "travel", null, FlashcardSetVisibility.SELECTED_FRIENDS,
                        List.of(friend), List.of(), created.version()));

        assertThat(revisedCard.id()).isEqualTo(cardId);
        assertThat(added.orderNo()).isEqualTo(2);
        assertThat(count("flashcard_reviews where flashcard_id = :id", cardId)).isOne();
        assertThat(revised.version()).isGreaterThan(created.version());
        assertThat(revised.sharedWith()).extracting(PersonalSetResult.Person::id).containsExactly(friend);
        assertThat(revised.cards()).extracting(c -> c.definitionVi()).containsExactly("tấm vé", "cổng");

        // Back to public: the friend list goes, the set is open to anyone not blocked.
        personalFlashcardService.updateSet(created.id(), new SavePersonalSetCommand("Airport", "", "travel", null,
                FlashcardSetVisibility.PUBLIC, List.of(), List.of(), revised.version()));
        assertThat(count("flashcard_set_shares where flashcard_set_id = :id", created.id())).isZero();
        SignedIn.as(jdbc, stranger);
        assertThat(flashcardService.setSummary(created.id()).ownerId()).isEqualTo(owner);
    }

    private static PersonalCardCommand card(String lemma, String meaning) {
        return new PersonalCardCommand(lemma, meaning, null, null, null, null, null, null);
    }

    private long count(String fromWhere, UUID id) {
        return jdbc.sql("select count(*) from " + fromWhere).param("id", id).query(Long.class).single();
    }

    private static List<UUID> ids(org.springframework.data.domain.Page<FlashcardSetSummaryResult> page) {
        return page.getContent().stream().map(FlashcardSetSummaryResult::id).toList();
    }
}
