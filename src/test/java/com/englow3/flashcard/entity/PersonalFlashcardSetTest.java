package com.englow3.flashcard.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.flashcard.dto.command.PersonalCardCommand;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.DomainException;

/** A learner's own set: who may see it, what its audience must look like, and that it stays out of review. */
class PersonalFlashcardSetTest {

    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();

    @Test
    void isLiveAndOwnedFromTheMomentItIsCreated() {
        FlashcardSet set = personal(FlashcardSetVisibility.PRIVATE, 0);

        assertThat(set.getKind()).isEqualTo(FlashcardSetKind.PERSONAL);
        assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PUBLISHED);
        assertThat(set.getPublishedAt()).isEqualTo(NOW);
        assertThat(set.getSlug()).isEqualTo("personal-" + set.getId());
        assertThat(set.isOwnedBy(OWNER)).isTrue();
        assertThat(set.isOwnedBy(VIEWER)).isFalse();
    }

    @Test
    void refusesASetWithNoVisibility() {
        assertThatThrownBy(() -> personal(null, 0)).isInstanceOf(BadRequestException.class).extracting(this::code)
                .isEqualTo("FLASHCARD_SET_VISIBILITY_REQUIRED");
    }

    /** Shared with nobody would be a private set under another name. */
    @Test
    void refusesSelectedFriendsWithNobodySelected() {
        assertThatThrownBy(() -> personal(FlashcardSetVisibility.SELECTED_FRIENDS, 0))
                .isInstanceOf(BadRequestException.class).extracting(this::code)
                .isEqualTo("FLASHCARD_SET_NO_FRIENDS_SELECTED");
    }

    @Test
    void refusesAFriendListOnAPublicOrPrivateSet() {
        assertThatThrownBy(() -> personal(FlashcardSetVisibility.PUBLIC, 2)).isInstanceOf(BadRequestException.class)
                .extracting(this::code).isEqualTo("FLASHCARD_SET_FRIENDS_NOT_ALLOWED");
        assertThatThrownBy(() -> personal(FlashcardSetVisibility.PRIVATE, 1)).isInstanceOf(BadRequestException.class)
                .extracting(this::code).isEqualTo("FLASHCARD_SET_FRIENDS_NOT_ALLOWED");
    }

    @Test
    void revisesItsDetailsAndAudience() {
        FlashcardSet set = personal(FlashcardSetVisibility.PRIVATE, 0);

        set.revisePersonal("Travel words", "For Da Nang", "travel", "A2", FlashcardSetVisibility.SELECTED_FRIENDS, 3,
                set.getVersion(), NOW.plusSeconds(60));

        assertThat(set.getName()).isEqualTo("Travel words");
        assertThat(set.getTopic()).isEqualTo("travel");
        assertThat(set.getVisibility()).isEqualTo(FlashcardSetVisibility.SELECTED_FRIENDS);
        assertThat(set.getAuthoringUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void refusesARevisionMadeFromAStaleCopy() {
        FlashcardSet set = personal(FlashcardSetVisibility.PRIVATE, 0);

        assertThatThrownBy(() -> set.revisePersonal("x", "", "t", null, FlashcardSetVisibility.PUBLIC, 0,
                set.getVersion() + 1, NOW)).isInstanceOf(ConflictException.class).extracting(this::code)
                        .isEqualTo("CONTENT_CHANGED");
        assertThat(set.getVisibility()).isEqualTo(FlashcardSetVisibility.PRIVATE);
    }

    @Test
    void revisionStillChecksTheAudience() {
        FlashcardSet set = personal(FlashcardSetVisibility.PUBLIC, 0);

        assertThatThrownBy(() -> set.revisePersonal("x", "", "t", null, FlashcardSetVisibility.SELECTED_FRIENDS, 0,
                set.getVersion(), NOW)).isInstanceOf(BadRequestException.class).extracting(this::code)
                        .isEqualTo("FLASHCARD_SET_NO_FRIENDS_SELECTED");
    }

    @Test
    void aCatalogueSetIsNotRevisedAsAPersonalOne() {
        FlashcardSet catalogue = catalogue();

        assertThatThrownBy(() -> catalogue.revisePersonal("x", "", "t", null, FlashcardSetVisibility.PRIVATE, 0,
                catalogue.getVersion(), NOW)).isInstanceOf(ConflictException.class).extracting(this::code)
                        .isEqualTo("FLASHCARD_SET_NOT_PERSONAL");
    }

    /** Otherwise a learner could publish around review, or staff could archive a learner's set. */
    @Test
    void neverEntersTheReviewLifecycle() {
        FlashcardSet set = personal(FlashcardSetVisibility.PUBLIC, 0);

        assertThatThrownBy(() -> set.submitForReview(5, NOW)).extracting(this::code)
                .isEqualTo("FLASHCARD_SET_PERSONAL");
        assertThatThrownBy(() -> set.approve(VIEWER, 5, NOW)).extracting(this::code)
                .isEqualTo("FLASHCARD_SET_PERSONAL");
        assertThatThrownBy(() -> set.reject(VIEWER, "no", NOW)).extracting(this::code)
                .isEqualTo("FLASHCARD_SET_PERSONAL");
        assertThatThrownBy(() -> set.publish(5, NOW)).extracting(this::code).isEqualTo("FLASHCARD_SET_PERSONAL");
        assertThatThrownBy(set::archive).extracting(this::code).isEqualTo("FLASHCARD_SET_PERSONAL");
        assertThatThrownBy(() -> set.requireAppendable(true)).extracting(this::code)
                .isEqualTo("FLASHCARD_SET_PERSONAL");
    }

    @Test
    void theOwnerAlwaysSeesTheirSet() {
        assertThat(personal(FlashcardSetVisibility.PRIVATE, 0).isVisibleTo(OWNER, false, false)).isTrue();
    }

    @Test
    void aPrivateSetIsHiddenFromEveryoneElse() {
        assertThat(personal(FlashcardSetVisibility.PRIVATE, 0).isVisibleTo(VIEWER, false, true)).isFalse();
    }

    @Test
    void aPublicSetIsShownToAnyoneNotBlocked() {
        FlashcardSet set = personal(FlashcardSetVisibility.PUBLIC, 0);

        assertThat(set.isVisibleTo(VIEWER, false, false)).isTrue();
        assertThat(set.isVisibleTo(VIEWER, true, false)).isFalse();
    }

    @Test
    void aSelectedFriendsSetIsShownOnlyToASelectedFriend() {
        FlashcardSet set = personal(FlashcardSetVisibility.SELECTED_FRIENDS, 1);

        assertThat(set.isVisibleTo(VIEWER, false, true)).isTrue();
        assertThat(set.isVisibleTo(VIEWER, false, false)).isFalse();
        assertThat(set.isVisibleTo(VIEWER, true, true)).isFalse();
    }

    @Test
    void aCatalogueSetIsVisibleOncePublishedAndOwnedByNobody() {
        FlashcardSet catalogue = catalogue();

        assertThat(catalogue.isVisibleTo(VIEWER, false, false)).isFalse();
        catalogue.publish(1, NOW);
        assertThat(catalogue.isVisibleTo(VIEWER, true, false)).isTrue();
        assertThat(catalogue.isOwnedBy(OWNER)).isFalse();
    }

    /** In place, because review rows hang off the card id. */
    @Test
    void aPersonalCardIsRevisedInPlace() {
        Flashcard card = Flashcard.personal(UUID.randomUUID(), 1,
                new PersonalCardCommand("ticket", "vé", null, null, null, null, null, null));
        UUID id = card.getId();

        card.revise(new PersonalCardCommand("boarding pass", "thẻ lên máy bay", "noun", " ", null,
                "Show your boarding pass.", null, null));

        assertThat(card.getId()).isEqualTo(id);
        assertThat(card.getLemma()).isEqualTo("boarding pass");
        assertThat(card.getPartOfSpeech()).isEqualTo("noun");
        assertThat(card.getIpaUs()).isNull();
        assertThat(card.getExampleSentence()).isEqualTo("Show your boarding pass.");
    }

    private static FlashcardSet personal(FlashcardSetVisibility visibility, int friends) {
        return FlashcardSet.personal("Words", null, "general", null, visibility, friends, OWNER, NOW);
    }

    private static FlashcardSet catalogue() {
        return FlashcardSet.draft("core", "Core", "", "general", "B1", OWNER);
    }

    private String code(Throwable e) {
        return ((DomainException) e).getCode();
    }
}
