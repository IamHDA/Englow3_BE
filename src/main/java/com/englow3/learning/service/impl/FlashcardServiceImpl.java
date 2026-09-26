package com.englow3.learning.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.command.RateFlashcardCommand;
import com.englow3.learning.dto.result.FlashcardResult;
import com.englow3.learning.dto.result.FlashcardReviewResult;
import com.englow3.learning.dto.result.FlashcardSetDetailResult;
import com.englow3.learning.dto.result.FlashcardSetSummaryResult;
import com.englow3.learning.entity.Flashcard;
import com.englow3.learning.entity.FlashcardReview;
import com.englow3.learning.entity.FlashcardReviewLog;
import com.englow3.learning.entity.FlashcardSet;
import com.englow3.learning.entity.FlashcardSetStatus;
import com.englow3.learning.repository.FlashcardRepository;
import com.englow3.learning.repository.FlashcardReviewLogRepository;
import com.englow3.learning.repository.FlashcardReviewRepository;
import com.englow3.learning.repository.FlashcardSetRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.UserDirectory;
import com.englow3.learning.service.*;

/**
 * Reading the catalogue and answering cards. Scheduling itself is {@link FlashcardSrs}; this class decides which cards
 * to show, writes the outcome, and appends the log row the statistics are later built from.
 */
@Service
public class FlashcardServiceImpl implements FlashcardService {

    private final FlashcardSetRepository setRepo;
    private final FlashcardRepository cardRepo;
    private final FlashcardReviewRepository reviewRepo;
    private final FlashcardReviewLogRepository reviewLogRepo;
    private final UserDirectory userDirectory;
    private final PresignedUrlResolver presignedUrls;
    private final String learningBucket;
    private final Duration mediaUrlTtl;

    public FlashcardServiceImpl(FlashcardSetRepository setRepo, FlashcardRepository cardRepo,
            FlashcardReviewRepository reviewRepo, FlashcardReviewLogRepository reviewLogRepo,
            UserDirectory userDirectory, PresignedUrlResolver presignedUrls,
            @Value("${app.storage.learning-bucket}") String learningBucket,
            @Value("${app.storage.learning-media-url-ttl:PT3H}") Duration mediaUrlTtl) {
        this.setRepo = setRepo;
        this.cardRepo = cardRepo;
        this.reviewRepo = reviewRepo;
        this.reviewLogRepo = reviewLogRepo;
        this.userDirectory = userDirectory;
        this.presignedUrls = presignedUrls;
        this.learningBucket = learningBucket;
        this.mediaUrlTtl = mediaUrlTtl;
    }

    @Transactional(readOnly = true)
    public Page<FlashcardSetSummaryResult> searchPublishedSets(String topic, String title, Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = Instant.now();

        Page<FlashcardSet> page = setRepo.searchByStatus(FlashcardSetStatus.PUBLISHED, topic, title, pageable);
        List<UUID> setIds = page.getContent().stream().map(FlashcardSet::getId).toList();
        Map<UUID, Long> cardCounts = countCardsFor(setIds);
        Map<UUID, Instant> lastStudied = lastStudiedFor(userId, setIds);

        // Due and mastered for the whole page in one query: per set it was two round trips each, and with the
        // database a region away a page of twenty took seconds.
        Map<UUID, long[]> progress = reviewRepo.countDueAndMasteredBySet(userId, setIds, now);
        return page.map(set -> {
            long[] dueAndMastered = progress.getOrDefault(set.getId(), new long[2]);
            return FlashcardSetSummaryResult.of(set, cardCounts.getOrDefault(set.getId(), 0L), dueAndMastered[0],
                    dueAndMastered[1], lastStudied.get(set.getId()));
        });
    }

    @Transactional(readOnly = true)
    public FlashcardSetDetailResult setDetail(UUID setId) {
        UUID userId = userDirectory.requireCurrentUserId();
        FlashcardSet set = requirePublishedSet(setId);

        List<Flashcard> cards = cardRepo.findByFlashcardSetIdOrderByOrderNo(setId);
        Map<UUID, FlashcardReview> reviews = reviewsFor(userId, cards);

        return new FlashcardSetDetailResult(summaryOf(set, userId, cards.size()),
                cards.stream().map(card -> resultOf(card, reviews.get(card.getId()))).toList());
    }

    /**
     * The study queue: what is due now, then unseen cards to fill the session. Due cards come first because a card the
     * learner is about to forget is worth more than a new one - that is the entire premise of spaced repetition, and
     * showing new cards first would quietly undo it.
     */
    @Transactional(readOnly = true)
    public List<FlashcardResult> studyQueue(UUID setId, int limit) {
        UUID userId = userDirectory.requireCurrentUserId();
        requirePublishedSet(setId);

        List<Flashcard> cards = cardRepo.findByFlashcardSetIdOrderByOrderNo(setId);
        Map<UUID, FlashcardReview> reviews = reviewsFor(userId, cards);
        Instant now = Instant.now();

        List<FlashcardResult> due = cards.stream().filter(card -> {
            FlashcardReview review = reviews.get(card.getId());
            return review != null && !review.getDueAt().isAfter(now);
        }).map(card -> resultOf(card, reviews.get(card.getId()))).toList();

        List<FlashcardResult> unseen = cards.stream().filter(card -> !reviews.containsKey(card.getId()))
                .map(card -> resultOf(card, null)).toList();

        return java.util.stream.Stream.concat(due.stream(), unseen.stream()).limit(limit).toList();
    }

    /**
     * Records one answer. The review row is created on first sight rather than seeded for the whole catalogue when a
     * learner opens a set - a learner who browses ten sets and studies one should not leave rows behind in the other
     * nine.
     */
    @Transactional
    public FlashcardReviewResult rate(RateFlashcardCommand command) {
        UUID userId = userDirectory.requireCurrentUserId();
        Flashcard card = cardRepo.findById(command.flashcardId())
                .orElseThrow(() -> new NotFoundException("FLASHCARD_NOT_FOUND",
                        "No flashcard with id %s".formatted(command.flashcardId())));
        // The set has to be published, not just the card to exist. Without this a guessed id puts a card from someone
        // else's draft into this learner's review schedule, and the same refusal for "not yours" and "not there" is
        // what stops an id being a way to find out which drafts exist.
        requirePublishedSet(card.getFlashcardSetId());
        Instant now = Instant.now();

        FlashcardReview review = reviewRepo.findByUserIdAndFlashcardId(userId, card.getId())
                .orElseGet(() -> reviewRepo.save(FlashcardReview.unseen(userId, card.getId(), now)));

        review.applySchedule(command.rating(), FlashcardSrs.schedule(review, command.rating(), now), now);

        reviewLogRepo.save(FlashcardReviewLog.of(userId, card.getId(), card.getFlashcardSetId(), command.rating(),
                Math.max(0, command.timeSpentSeconds()), now));

        return FlashcardReviewResult.of(review);
    }

    private FlashcardSetSummaryResult summaryOf(FlashcardSet set, UUID userId, long cardCount) {
        return FlashcardSetSummaryResult.of(set, cardCount,
                reviewRepo.countDueInSet(userId, set.getId(), Instant.now()),
                reviewRepo.countMasteredInSet(userId, set.getId()),
                lastStudiedFor(userId, List.of(set.getId())).get(set.getId()));
    }

    private Map<UUID, Instant> lastStudiedFor(UUID userId, Collection<UUID> setIds) {
        if (setIds.isEmpty()) {
            return Map.of();
        }
        return reviewLogRepo.findLastStudiedAtBySet(userId, setIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Instant) row[1]));
    }

    private Map<UUID, FlashcardReview> reviewsFor(UUID userId, List<Flashcard> cards) {
        if (cards.isEmpty()) {
            return Map.of();
        }
        return reviewRepo.findByUserIdAndFlashcardIdIn(userId, cards.stream().map(Flashcard::getId).toList()).stream()
                .collect(Collectors.toMap(FlashcardReview::getFlashcardId, Function.identity()));
    }

    /** The empty-input guard and the row mapping moved into the repository, where the admin list needs them too. */
    private Map<UUID, Long> countCardsFor(Collection<UUID> setIds) {
        return cardRepo.countBySetIds(setIds);
    }

    private FlashcardSet requirePublishedSet(UUID setId) {
        return setRepo.findById(setId).filter(set -> set.getStatus() == FlashcardSetStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("FLASHCARD_SET_NOT_FOUND",
                        "No published flashcard set with id %s".formatted(setId)));
    }

    private FlashcardResult resultOf(Flashcard card, FlashcardReview review) {
        return FlashcardResult.of(card, review,
                presignedUrls.resolve(learningBucket, card.getAudioUsObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(learningBucket, card.getAudioUkObjectKey(), mediaUrlTtl));
    }
}
