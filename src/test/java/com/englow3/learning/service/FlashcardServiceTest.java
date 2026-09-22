package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.learning.dto.command.RateFlashcardCommand;
import com.englow3.learning.entity.Flashcard;
import com.englow3.learning.entity.FlashcardSet;
import com.englow3.learning.entity.ReviewRating;
import com.englow3.learning.repository.FlashcardRepository;
import com.englow3.learning.repository.FlashcardReviewLogRepository;
import com.englow3.learning.repository.FlashcardReviewRepository;
import com.englow3.learning.repository.FlashcardSetRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

/**
 * Rating a card is the one write a learner can aim at an arbitrary id, so what it refuses is the part worth pinning
 * down.
 */
class FlashcardServiceTest {

    private final FlashcardSetRepository setRepo = mock(FlashcardSetRepository.class);
    private final FlashcardRepository cardRepo = mock(FlashcardRepository.class);
    private final FlashcardReviewRepository reviewRepo = mock(FlashcardReviewRepository.class);
    private final FlashcardReviewLogRepository reviewLogRepo = mock(FlashcardReviewLogRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);

    private final FlashcardService service = new FlashcardService(setRepo, cardRepo, reviewRepo, reviewLogRepo,
            userDirectory);

    private final UUID userId = UUID.randomUUID();
    private Flashcard card;

    @BeforeEach
    void setUp() {
        UUID setId = UUID.randomUUID();
        card = Flashcard.of(setId, 1, "agenda", "noun", "agenda (meeting)", "/əˈdʒendə/", null, null, null,
                "A list of items to discuss.", "Chương trình nghị sự.", "Send the agenda.", null, null, "B1");

        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(cardRepo.findById(card.getId())).thenReturn(Optional.of(card));
        when(reviewRepo.findByUserIdAndFlashcardId(userId, card.getId())).thenReturn(Optional.empty());
        when(reviewRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setIsPublished(boolean published) {
        FlashcardSet set = FlashcardSet.draft("core-500", "Core 500", "", "general", "B1", UUID.randomUUID());
        if (published) {
            set.publish(1, java.time.Instant.now());
        }
        when(setRepo.findById(card.getFlashcardSetId())).thenReturn(Optional.of(set));
    }

    @Test
    void schedulesACardFromAPublishedSet() {
        setIsPublished(true);

        var result = service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, 12));

        assertThat(result.flashcardId()).isEqualTo(card.getId());
        verify(reviewLogRepo).save(any());
    }

    /**
     * The hole this closes. A card id is guessable, and without this check one from somebody's unpublished draft joins
     * the guesser's review schedule.
     */
    @Test
    void refusesACardWhoseSetIsNotPublished() {
        setIsPublished(false);

        assertThatThrownBy(() -> service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, 12)))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("FLASHCARD_SET_NOT_FOUND");

        // Nothing is written on the way to the refusal - no review row, no log entry.
        verify(reviewRepo, never()).save(any());
        verify(reviewLogRepo, never()).save(any());
    }

    /**
     * "Not published" and "does not exist" answer identically on purpose: a different code for each would turn a card
     * id into a way to find out which drafts exist.
     */
    @Test
    void answersAMissingSetAndAnUnpublishedSetTheSameWay() {
        when(setRepo.findById(card.getFlashcardSetId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, 12)))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("FLASHCARD_SET_NOT_FOUND");
    }

    @Test
    void refusesACardThatDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        when(cardRepo.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(new RateFlashcardCommand(unknown, ReviewRating.GOOD, 12)))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("FLASHCARD_NOT_FOUND");
    }

    /** A client sending a negative duration is clamped rather than refused - it is a stopwatch, not an answer. */
    @Test
    void doesNotLetANegativeDurationReachTheLog() {
        setIsPublished(true);

        service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, -30));

        var saved = org.mockito.ArgumentCaptor.forClass(com.englow3.learning.entity.FlashcardReviewLog.class);
        verify(reviewLogRepo).save(saved.capture());
        assertThat(saved.getValue().getTimeSpentSeconds()).isZero();
    }
}
