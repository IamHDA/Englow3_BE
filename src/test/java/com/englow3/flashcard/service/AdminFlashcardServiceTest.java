package com.englow3.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.flashcard.dto.command.AddFlashcardsCommand;
import com.englow3.flashcard.dto.command.AddFlashcardsCommand.NewCard;
import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;
import com.englow3.flashcard.entity.Flashcard;
import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.FlashcardSetStatus;
import com.englow3.flashcard.repository.FlashcardRepository;
import com.englow3.flashcard.repository.FlashcardSetRepository;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Orchestration only. The publish and archive rules live in FlashcardSet and are covered there; this asserts which
 * repository call the service makes and with what.
 */
class AdminFlashcardServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    private final FlashcardSetRepository setRepo = mock(FlashcardSetRepository.class);
    private final FlashcardRepository cardRepo = mock(FlashcardRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);

    private final AdminFlashcardService service = new com.englow3.flashcard.service.impl.AdminFlashcardServiceImpl(
            setRepo, cardRepo, userDirectory, currentUser, new ObjectMapper(), CLOCK);

    private final UUID authorId = UUID.randomUUID();
    private FlashcardSet set;

    @BeforeEach
    void setUp() {
        set = FlashcardSet.draft("toeic-unit-1", "TOEIC Unit 1", "", "TOEIC", "B1", authorId);
        when(userDirectory.requireCurrentUserId()).thenReturn(authorId);
        when(setRepo.findById(set.getId())).thenReturn(Optional.of(set));
        when(setRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cardRepo.countByFlashcardSetId(set.getId())).thenReturn(0L);
    }

    private NewCard card(String lemma) {
        return new NewCard(lemma, "noun", lemma + " (sense 1)", "/test/", null, null, null, "A definition",
                "Mot dinh nghia", "An example.", null, null, "B1");
    }

    @Nested
    class CreatingASet {

        @Test
        void refusesASlugThatIsAlreadyTaken() {
            when(setRepo.existsBySlug("toeic-unit-1")).thenReturn(true);

            assertThatThrownBy(() -> service
                    .createSet(new CreateFlashcardSetCommand("toeic-unit-1", "Another", "", "TOEIC", "B1")))
                            .isInstanceOf(ConflictException.class)
                            .hasFieldOrPropertyWithValue("code", "FLASHCARD_SET_SLUG_TAKEN");
        }
    }

    @Nested
    class AddingCards {

        @Test
        void numbersANewSetFromOne() {
            when(cardRepo.findMaxOrderNo(set.getId())).thenReturn(Optional.empty());

            service.addCards(new AddFlashcardsCommand(set.getId(), List.of(card("alpha"), card("beta"))));

            assertThat(savedCards()).extracting(Flashcard::getOrderNo).containsExactly(1, 2);
        }

        /** A second batch continues where the first stopped; restarting at one would collide on (set, order_no). */
        @Test
        void continuesNumberingFromTheCardsAlreadyThere() {
            when(cardRepo.findMaxOrderNo(set.getId())).thenReturn(Optional.of(7));

            service.addCards(new AddFlashcardsCommand(set.getId(), List.of(card("gamma"))));

            assertThat(savedCards()).extracting(Flashcard::getOrderNo).containsExactly(8);
        }

        /** Appending is additive - a new card is simply unseen by every learner, so a live set can still grow. */
        @Test
        void letsAnAdministratorAppendToAPublishedSet() {
            set.publish(1L, java.time.Instant.now());
            when(currentUser.hasRole("ADMIN")).thenReturn(true);
            when(cardRepo.findMaxOrderNo(set.getId())).thenReturn(Optional.of(1));

            service.addCards(new AddFlashcardsCommand(set.getId(), List.of(card("delta"))));

            assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PUBLISHED);
            assertThat(savedCards()).hasSize(1);
        }

        /** Staff adding to a live set would put cards in front of learners that no reviewer has seen. */
        @Test
        void refusesStaffAppendingToAPublishedSet() {
            set.publish(1L, java.time.Instant.now());
            when(currentUser.hasRole("ADMIN")).thenReturn(false);

            assertThatThrownBy(() -> service.addCards(new AddFlashcardsCommand(set.getId(), List.of(card("delta")))))
                    .isInstanceOf(ForbiddenException.class).extracting(e -> ((ForbiddenException) e).getCode())
                    .isEqualTo("FLASHCARD_SET_LIVE_ADMIN_ONLY");
            verify(cardRepo, never()).saveAll(any());
        }

        /** What is approved has to be what was reviewed, so a set under review is frozen - for everyone. */
        @Test
        void refusesAppendingToASetUnderReview() {
            set.submitForReview(1L, java.time.Instant.now());
            when(currentUser.hasRole("ADMIN")).thenReturn(true);

            assertThatThrownBy(() -> service.addCards(new AddFlashcardsCommand(set.getId(), List.of(card("delta")))))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("FLASHCARD_SET_NOT_EDITABLE");
            verify(cardRepo, never()).saveAll(any());
        }

        @Test
        void refusesAppendingToAnArchivedSet() {
            set.archive();
            when(currentUser.hasRole("ADMIN")).thenReturn(true);

            assertThatThrownBy(() -> service.addCards(new AddFlashcardsCommand(set.getId(), List.of(card("delta")))))
                    .isInstanceOf(ConflictException.class);
        }

        @SuppressWarnings("unchecked")
        private List<Flashcard> savedCards() {
            ArgumentCaptor<List<Flashcard>> captor = ArgumentCaptor.forClass(List.class);
            verify(cardRepo).saveAll(captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    class Publishing {

        @Test
        void refusesASetWithNoCards() {
            when(cardRepo.countByFlashcardSetId(set.getId())).thenReturn(0L);

            assertThatThrownBy(() -> service.publish(set.getId())).isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code", "FLASHCARD_SET_EMPTY");
        }

        @Test
        void publishesASetThatHasCards() {
            when(cardRepo.countByFlashcardSetId(set.getId())).thenReturn(12L);

            service.publish(set.getId());

            assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PUBLISHED);
            assertThat(set.getPublishedAt()).isNotNull();
        }
    }
}
