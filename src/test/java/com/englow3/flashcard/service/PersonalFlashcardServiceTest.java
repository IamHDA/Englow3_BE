package com.englow3.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.flashcard.dto.command.PersonalCardCommand;
import com.englow3.flashcard.dto.command.SavePersonalSetCommand;
import com.englow3.flashcard.dto.result.PersonalSetResult;
import com.englow3.flashcard.entity.Flashcard;
import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.FlashcardSetShare;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import com.englow3.flashcard.repository.FlashcardRepository;
import com.englow3.flashcard.repository.FlashcardSetRepository;
import com.englow3.flashcard.repository.FlashcardSetShareRepository;
import com.englow3.flashcard.service.impl.PersonalFlashcardServiceImpl;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.DomainException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

/**
 * The editor's orchestration: what it saves and in which order, which people it checks against the friendship module,
 * and what it refuses. The audience rules themselves are {@code PersonalFlashcardSetTest}'s.
 */
class PersonalFlashcardServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-28T10:00:00Z"), ZoneOffset.UTC);

    private final FlashcardSetRepository flashcardSetRepo = mock(FlashcardSetRepository.class);
    private final FlashcardRepository flashcardRepo = mock(FlashcardRepository.class);
    private final FlashcardSetShareRepository flashcardSetShareRepo = mock(FlashcardSetShareRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);

    private final PersonalFlashcardService service = new PersonalFlashcardServiceImpl(flashcardSetRepo, flashcardRepo,
            flashcardSetShareRepo, userDirectory, friendshipReader, publicProfileReader, CLOCK);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID friendId = UUID.randomUUID();
    private final PersonalCardCommand ticket = new PersonalCardCommand("ticket", "vé", null, null, null, null, null,
            null);

    @BeforeEach
    void setUp() {
        when(userDirectory.requireCurrentUserId()).thenReturn(ownerId);
        when(flashcardSetRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(flashcardRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsTheSetItsSharesAndItsCardsInOrder() {
        when(friendshipReader.friendIdsOf(ownerId)).thenReturn(Set.of(friendId));
        when(flashcardSetShareRepo.findUserIdsBySetId(any())).thenReturn(List.of(friendId));
        when(publicProfileReader.profiles(List.of(friendId)))
                .thenReturn(Map.of(friendId, new PublicProfileReader.PublicProfile(friendId, "Lan", null)));
        PersonalCardCommand plane = new PersonalCardCommand("plane", "máy bay", null, null, null, null, null, null);

        var result = service.createSet(new SavePersonalSetCommand("Travel", null, "travel", null,
                FlashcardSetVisibility.SELECTED_FRIENDS, List.of(friendId, friendId), List.of(ticket, plane), null));

        ArgumentCaptor<FlashcardSet> set = ArgumentCaptor.forClass(FlashcardSet.class);
        verify(flashcardSetRepo).save(set.capture());
        assertThat(set.getValue().isOwnedBy(ownerId)).isTrue();

        // A duplicated id is one share, not two.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FlashcardSetShare>> shares = ArgumentCaptor.forClass(List.class);
        verify(flashcardSetShareRepo).saveAll(shares.capture());
        assertThat(shares.getValue()).singleElement()
                .satisfies(share -> assertThat(share.getId().getUserId()).isEqualTo(friendId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Flashcard>> cards = ArgumentCaptor.forClass(List.class);
        verify(flashcardRepo).saveAll(cards.capture());
        assertThat(cards.getValue()).extracting(Flashcard::getLemma).containsExactly("ticket", "plane");
        assertThat(cards.getValue()).extracting(Flashcard::getOrderNo).containsExactly(1, 2);

        assertThat(result.sharedWith()).singleElement().satisfies(p -> assertThat(p.displayName()).isEqualTo("Lan"));
    }

    @Test
    void refusesToShareWithSomeoneWhoIsNotAFriendAndSavesNothing() {
        when(friendshipReader.friendIdsOf(ownerId)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.createSet(new SavePersonalSetCommand("Travel", null, "travel", null,
                FlashcardSetVisibility.SELECTED_FRIENDS, List.of(friendId), List.of(), null)))
                        .isInstanceOf(BadRequestException.class).extracting(e -> ((DomainException) e).getCode())
                        .isEqualTo("FLASHCARD_SHARE_NOT_FRIEND");

        verify(flashcardSetRepo, never()).save(any());
        verify(flashcardSetShareRepo, never()).saveAll(anyList());
    }

    @Test
    void aPublicSetNeverAsksTheFriendshipModule() {
        service.createSet(new SavePersonalSetCommand("Travel", null, "travel", null, FlashcardSetVisibility.PUBLIC,
                List.of(), List.of(), null));

        verify(friendshipReader, never()).friendIdsOf(any());
    }

    /** Someone else's set answers exactly like a missing one, whether or not the caller can study it. */
    @Test
    void answersNotFoundForASetTheCallerDoesNotOwn() {
        FlashcardSet set = mock(FlashcardSet.class);
        UUID setId = UUID.randomUUID();
        when(flashcardSetRepo.findById(setId)).thenReturn(Optional.of(set));
        when(set.isOwnedBy(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> service.ownSet(setId)).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo("FLASHCARD_SET_NOT_FOUND");
        assertThatThrownBy(() -> service.deleteSet(setId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.addCard(setId, ticket)).isInstanceOf(NotFoundException.class);
        verify(flashcardSetRepo, never()).delete(any());
        verify(flashcardRepo, never()).save(any());
    }

    @Test
    void anEditNeedsTheVersionItWasMadeFrom() {
        assertThatThrownBy(() -> service.updateSet(UUID.randomUUID(),
                new SavePersonalSetCommand("x", null, "t", null, FlashcardSetVisibility.PUBLIC, List.of(), List.of(),
                        null))).isInstanceOf(BadRequestException.class).extracting(e -> ((DomainException) e).getCode())
                                .isEqualTo("CONTENT_VERSION_REQUIRED");
    }

    @Test
    void anEditHandsTheSetItsNewDetailsThenReplacesTheShareList() {
        FlashcardSet set = ownSet();
        when(friendshipReader.friendIdsOf(ownerId)).thenReturn(Set.of(friendId));
        when(set.getVisibility()).thenReturn(FlashcardSetVisibility.SELECTED_FRIENDS);

        service.updateSet(set.getId(), new SavePersonalSetCommand("Travel", "d", "travel", "A2",
                FlashcardSetVisibility.SELECTED_FRIENDS, List.of(friendId), List.of(ticket), 4L));

        var order = inOrder(set, flashcardSetShareRepo, flashcardSetRepo);
        order.verify(set).revisePersonal("Travel", "d", "travel", "A2", FlashcardSetVisibility.SELECTED_FRIENDS, 1, 4L,
                CLOCK.instant());
        order.verify(flashcardSetShareRepo).deleteBySetId(set.getId());
        order.verify(flashcardSetShareRepo).saveAll(anyList());
        order.verify(flashcardSetRepo).flush();
        // Cards are edited one at a time after create; an edit of the set never touches them.
        verify(flashcardRepo, never()).saveAll(anyList());
    }

    /** A share row left behind by an unfriending grants nothing, so the editor does not show it either. */
    @Test
    void theEditorListsOnlyPeopleWhoAreStillFriends() {
        FlashcardSet set = ownSet();
        UUID formerFriend = UUID.randomUUID();
        when(set.getVisibility()).thenReturn(FlashcardSetVisibility.SELECTED_FRIENDS);
        when(flashcardSetShareRepo.findUserIdsBySetId(set.getId())).thenReturn(List.of(friendId, formerFriend));
        when(friendshipReader.friendIdsOf(ownerId)).thenReturn(Set.of(friendId));
        when(publicProfileReader.profiles(List.of(friendId)))
                .thenReturn(Map.of(friendId, new PublicProfileReader.PublicProfile(friendId, "Lan", null)));

        var result = service.ownSet(set.getId());

        assertThat(result.sharedWith()).extracting(PersonalSetResult.Person::id).containsExactly(friendId);
    }

    @Test
    void addsACardAfterTheLastOne() {
        FlashcardSet set = ownSet();
        when(flashcardRepo.findMaxOrderNo(set.getId())).thenReturn(Optional.of(7));

        var card = service.addCard(set.getId(), ticket);

        assertThat(card.orderNo()).isEqualTo(8);
        assertThat(card.lemma()).isEqualTo("ticket");
    }

    @Test
    void refusesToEditACardFromAnotherSet() {
        FlashcardSet set = ownSet();
        Flashcard elsewhere = Flashcard.personal(UUID.randomUUID(), 1, ticket);
        when(flashcardRepo.findById(elsewhere.getId())).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.updateCard(set.getId(), elsewhere.getId(), ticket))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((DomainException) e).getCode())
                .isEqualTo("FLASHCARD_NOT_FOUND");
        assertThatThrownBy(() -> service.deleteCard(set.getId(), elsewhere.getId()))
                .isInstanceOf(NotFoundException.class);
        verify(flashcardRepo, never()).delete(any());
    }

    @Test
    void deletesTheOwnersSet() {
        FlashcardSet set = ownSet();

        service.deleteSet(set.getId());

        verify(flashcardSetRepo).delete(eq(set));
    }

    private FlashcardSet ownSet() {
        FlashcardSet set = mock(FlashcardSet.class);
        UUID setId = UUID.randomUUID();
        when(set.getId()).thenReturn(setId);
        when(set.getCreatedByUserId()).thenReturn(ownerId);
        when(set.isOwnedBy(ownerId)).thenReturn(true);
        when(set.getVisibility()).thenReturn(FlashcardSetVisibility.PRIVATE);
        when(flashcardSetRepo.findById(setId)).thenReturn(Optional.of(set));
        return set;
    }
}
