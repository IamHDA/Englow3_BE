package com.englow3.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.flashcard.dto.command.RateFlashcardCommand;
import com.englow3.flashcard.entity.Flashcard;
import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.ReviewRating;
import com.englow3.flashcard.repository.FlashcardRepository;
import com.englow3.flashcard.repository.FlashcardReviewLogRepository;
import com.englow3.flashcard.repository.FlashcardReviewRepository;
import com.englow3.flashcard.repository.FlashcardSetRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.UserDirectory;

/**
 * Rating a card is the one write a learner can aim at an arbitrary id, so what it refuses is the part worth pinning
 * down.
 */
class FlashcardServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    private final FlashcardSetRepository setRepo = mock(FlashcardSetRepository.class);
    private final FlashcardRepository cardRepo = mock(FlashcardRepository.class);
    private final FlashcardReviewRepository reviewRepo = mock(FlashcardReviewRepository.class);
    private final FlashcardReviewLogRepository reviewLogRepo = mock(FlashcardReviewLogRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);

    private final FlashcardService service = new com.englow3.flashcard.service.impl.FlashcardServiceImpl(setRepo,
            cardRepo, reviewRepo, reviewLogRepo, userDirectory, presignedUrls, CLOCK, "learning",
            java.time.Duration.ofHours(3));

    private final UUID userId = UUID.randomUUID();
    private Flashcard card;

    @BeforeEach
    void setUp() {
        UUID setId = UUID.randomUUID();
        card = Flashcard.of(setId, 1, "agenda", "noun", "agenda (meeting)", "/əˈdʒendə/", null, "audio/us.mp3",
                "audio/uk.mp3", "A list of items to discuss.", "Chương trình nghị sự.", "Send the agenda.", null, null,
                "B1");

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

    @Test
    void resolvesAudioUrlsBeforeBuildingTheStudyResult() {
        setIsPublished(true);
        // The queue asks for due cards and unseen cards by query, a session's worth each - not the whole set.
        when(reviewRepo.findDueCardsInSet(eq(userId), eq(card.getFlashcardSetId()), any(), any()))
                .thenReturn(List.of());
        when(cardRepo.findUnseenInSet(eq(userId), eq(card.getFlashcardSetId()), any())).thenReturn(List.of(card));
        when(presignedUrls.resolve("learning", "audio/us.mp3", java.time.Duration.ofHours(3)))
                .thenReturn("https://storage.example/us");
        when(presignedUrls.resolve("learning", "audio/uk.mp3", java.time.Duration.ofHours(3)))
                .thenReturn("https://storage.example/uk");

        var result = service.studyQueue(card.getFlashcardSetId(), 1).get(0);

        assertThat(result.audioUsUrl()).isEqualTo("https://storage.example/us");
        assertThat(result.audioUkUrl()).isEqualTo("https://storage.example/uk");
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

        var saved = org.mockito.ArgumentCaptor.forClass(com.englow3.flashcard.entity.FlashcardReviewLog.class);
        verify(reviewLogRepo).save(saved.capture());
        assertThat(saved.getValue().getTimeSpentSeconds()).isZero();
    }
}
