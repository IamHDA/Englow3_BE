package com.englow3.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.englow3.flashcard.dto.command.RateFlashcardCommand;
import com.englow3.flashcard.entity.Flashcard;
import com.englow3.flashcard.entity.FlashcardReview;
import com.englow3.flashcard.entity.FlashcardReviewLog;
import com.englow3.flashcard.entity.FlashcardReviewStatus;
import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.ReviewRating;
import com.englow3.flashcard.helper.FlashcardSrs;
import com.englow3.flashcard.repository.FlashcardRepository;
import com.englow3.flashcard.repository.FlashcardReviewLogRepository;
import com.englow3.flashcard.repository.FlashcardReviewRepository;
import com.englow3.flashcard.repository.FlashcardSetRepository;
import com.englow3.flashcard.repository.FlashcardSetShareRepository;
import com.englow3.flashcard.query.FlashcardCatalogueQuery;
import com.englow3.flashcard.dto.command.FlashcardSetScope;
import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
import com.englow3.flashcard.entity.FlashcardSetKind;
import com.englow3.flashcard.entity.FlashcardSetShareId;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.flashcard.service.impl.FlashcardServiceImpl;
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

    private final FlashcardCatalogueQuery catalogueQuery = mock(FlashcardCatalogueQuery.class);
    private final FlashcardSetShareRepository shareRepo = mock(FlashcardSetShareRepository.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);

    private final FlashcardService service = new FlashcardServiceImpl(setRepo, cardRepo, reviewRepo, reviewLogRepo,
            userDirectory, presignedUrls, CLOCK, "learning", Duration.ofHours(3), catalogueQuery, shareRepo,
            friendshipReader, publicProfileReader);

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
            set.publish(1, Instant.now());
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

    /** Going through the same session again a minute later is practice; it must not climb the intervals. */
    @Test
    void aCardAnsweredBeforeItIsDueKeepsItsSchedule() {
        setIsPublished(true);
        FlashcardReview review = reviewedOnceWithGood();
        Instant due = review.getDueAt();

        service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.EASY, 5));

        assertThat(review.getRepetitions()).isEqualTo((short) 1);
        assertThat(review.getDueAt()).isEqualTo(due);
        assertThat(review.getStatus()).isEqualTo(FlashcardReviewStatus.LEARNING);
        verify(reviewLogRepo).save(any());
    }

    @Test
    void aCardForgottenBeforeItIsDueIsStillSentBackToLearning() {
        setIsPublished(true);
        FlashcardReview review = reviewedOnceWithGood();

        service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.AGAIN, 5));

        assertThat(review.getRepetitions()).isZero();
        assertThat(review.getDueAt()).isEqualTo(CLOCK.instant().plus(Duration.ofMinutes(10)));
    }

    private FlashcardReview reviewedOnceWithGood() {
        FlashcardReview review = FlashcardReview.unseen(userId, card.getId(), CLOCK.instant());
        review.applySchedule(ReviewRating.GOOD, FlashcardSrs.schedule(review, ReviewRating.GOOD, CLOCK.instant()),
                CLOCK.instant());
        when(reviewRepo.findByUserIdAndFlashcardId(userId, card.getId())).thenReturn(Optional.of(review));
        return review;
    }

    @Test
    void detailLoadsOnlyTheRequestedCardPageAndKeepsTheWholeSetCount() {
        setIsPublished(true);
        var page = org.springframework.data.domain.PageRequest.of(1, 30);
        when(cardRepo.findByFlashcardSetId(card.getFlashcardSetId(), page))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(card), page, 300));
        var detail = service.setDetail(card.getFlashcardSetId(), page);
        assertThat(detail.cards()).hasSize(1);
        assertThat(detail.page()).isEqualTo(1);
        assertThat(detail.totalPages()).isEqualTo(10);
        assertThat(detail.set().cardCount()).isEqualTo(300);
        org.mockito.Mockito.verify(cardRepo, org.mockito.Mockito.never()).findByFlashcardSetIdOrderByOrderNo(any());
    }

    @Test
    void resolvesAudioUrlsBeforeBuildingTheStudyResult() {
        setIsPublished(true);
        // The queue asks for due cards and unseen cards by query, a session's worth each - not the whole set.
        when(reviewRepo.findDueCardsInSet(eq(userId), eq(card.getFlashcardSetId()), any(), any()))
                .thenReturn(List.of());
        when(cardRepo.findUnseenInSet(eq(userId), eq(card.getFlashcardSetId()), any())).thenReturn(List.of(card));
        when(presignedUrls.resolve("learning", "audio/us.mp3", Duration.ofHours(3)))
                .thenReturn("https://storage.example/us");
        when(presignedUrls.resolve("learning", "audio/uk.mp3", Duration.ofHours(3)))
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

        var saved = org.mockito.ArgumentCaptor.forClass(FlashcardReviewLog.class);
        verify(reviewLogRepo).save(saved.capture());
        assertThat(saved.getValue().getTimeSpentSeconds()).isZero();
    }

    /**
     * Someone else's personal set. The set decides; the service's part is to hand it the right facts - looked up from
     * the share list and the friendship module - and to refuse when the answer is no.
     */
    private FlashcardSet othersPersonalSet(FlashcardSetVisibility visibility, UUID ownerId) {
        FlashcardSet set = mock(FlashcardSet.class);
        when(set.getId()).thenReturn(card.getFlashcardSetId());
        when(set.isPersonal()).thenReturn(true);
        when(set.isOwnedBy(userId)).thenReturn(false);
        when(set.getCreatedByUserId()).thenReturn(ownerId);
        when(set.getVisibility()).thenReturn(visibility);
        when(set.getKind()).thenReturn(FlashcardSetKind.PERSONAL);
        when(setRepo.findById(card.getFlashcardSetId())).thenReturn(Optional.of(set));
        return set;
    }

    @Test
    void tellsASelectedFriendsSetThatTheReaderIsOnItsListAndStillAFriend() {
        UUID ownerId = UUID.randomUUID();
        FlashcardSet set = othersPersonalSet(FlashcardSetVisibility.SELECTED_FRIENDS, ownerId);
        when(shareRepo.existsById(new FlashcardSetShareId(card.getFlashcardSetId(), userId))).thenReturn(true);
        when(friendshipReader.areFriends(ownerId, userId)).thenReturn(true);
        when(set.isVisibleTo(userId, false, true)).thenReturn(true);

        service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, 5));

        verify(set).isVisibleTo(userId, false, true);
        verify(reviewLogRepo).save(any());
    }

    /** The share row outlives an unfriending; it is the live friendship that counts. */
    @Test
    void aShareRowWithoutAFriendshipIsNotPassedOnAsASelectedFriend() {
        UUID ownerId = UUID.randomUUID();
        FlashcardSet set = othersPersonalSet(FlashcardSetVisibility.SELECTED_FRIENDS, ownerId);
        when(shareRepo.existsById(new FlashcardSetShareId(card.getFlashcardSetId(), userId))).thenReturn(true);
        when(friendshipReader.areFriends(ownerId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.setSummary(card.getFlashcardSetId())).isInstanceOf(NotFoundException.class);

        verify(set).isVisibleTo(userId, false, false);
    }

    @Test
    void aPublicSetIsNotLookedUpInTheShareListButBlocksAreStillAsked() {
        UUID ownerId = UUID.randomUUID();
        FlashcardSet set = othersPersonalSet(FlashcardSetVisibility.PUBLIC, ownerId);
        when(friendshipReader.isBlockedEitherWay(ownerId, userId)).thenReturn(true);

        assertThatThrownBy(() -> service.setSummary(card.getFlashcardSetId())).isInstanceOf(NotFoundException.class);

        verify(set).isVisibleTo(userId, true, false);
        verifyNoInteractions(shareRepo);
    }

    /** Same refusal as a missing set, and nothing written on the way - a guessed id must not reveal a private set. */
    @Test
    void refusesToRateACardInASetTheLearnerMayNotSee() {
        othersPersonalSet(FlashcardSetVisibility.PRIVATE, UUID.randomUUID());

        assertThatThrownBy(() -> service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, 12)))
                .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                .isEqualTo("FLASHCARD_SET_NOT_FOUND");
        verify(reviewRepo, never()).save(any());
        verify(reviewLogRepo, never()).save(any());
    }

    @Test
    void theCatalogueNeverAsksAboutFriendsOrBlocks() {
        setIsPublished(true);

        service.rate(new RateFlashcardCommand(card.getId(), ReviewRating.GOOD, 12));

        verifyNoInteractions(friendshipReader, shareRepo);
    }

    @Test
    void theCommunityShelfIsFilteredByTheLearnersFriendsAndBlocksAndNamesItsOwners() {
        UUID friend = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        var shared = new FlashcardSetSummaryResult(UUID.randomUUID(), "personal-x", "Travel", "", "travel", null, 3, 0,
                0, null, FlashcardSetKind.PERSONAL, FlashcardSetVisibility.SELECTED_FRIENDS, friend, null, false);
        when(friendshipReader.friendIdsOf(userId)).thenReturn(Set.of(friend));
        when(friendshipReader.blockedEitherWay(userId)).thenReturn(Set.of(blocked));
        when(catalogueQuery.search(eq(userId), eq(Set.of(friend)), eq(Set.of(blocked)), any(),
                eq(FlashcardSetScope.COMMUNITY), any(), any(), eq("recent"), eq(pageable)))
                        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(shared), pageable, 1));
        when(publicProfileReader.profiles(Set.of(friend)))
                .thenReturn(Map.of(friend, new PublicProfileReader.PublicProfile(friend, "Lan", null)));

        var page = service.searchSets(FlashcardSetScope.COMMUNITY, null, null, "recent", pageable);

        assertThat(page.getContent()).singleElement()
                .satisfies(set -> assertThat(set.owner().displayName()).isEqualTo("Lan"));
    }

    @Test
    void theCatalogueShelfFetchesNoFriendsOrBlocks() {
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(catalogueQuery.search(eq(userId), eq(Set.of()), eq(Set.of()), any(), eq(FlashcardSetScope.CATALOGUE),
                any(), any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty(pageable));

        service.searchSets(FlashcardSetScope.CATALOGUE, null, null, "due", pageable);

        verifyNoInteractions(friendshipReader, publicProfileReader);
    }
}
