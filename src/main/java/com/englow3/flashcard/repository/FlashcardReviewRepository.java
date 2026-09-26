package com.englow3.flashcard.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.flashcard.entity.FlashcardReview;

public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, UUID> {

    Optional<FlashcardReview> findByUserIdAndFlashcardId(UUID userId, UUID flashcardId);

    List<FlashcardReview> findByUserIdAndFlashcardIdIn(UUID userId, Collection<UUID> flashcardIds);

    /** What the learner owes in one set right now, oldest debt first. */
    @Query("""
            select r from FlashcardReview r
            where r.userId = :userId and r.dueAt <= :now
              and r.flashcardId in (select c.id from Flashcard c where c.flashcardSetId = :setId)
            order by r.dueAt asc
            """)
    List<FlashcardReview> findDueInSet(@Param("userId") UUID userId, @Param("setId") UUID setId,
            @Param("now") Instant now);

    @Query("""
            select count(r) from FlashcardReview r
            where r.userId = :userId and r.dueAt <= :now
              and r.flashcardId in (select c.id from Flashcard c where c.flashcardSetId = :setId)
            """)
    long countDueInSet(@Param("userId") UUID userId, @Param("setId") UUID setId, @Param("now") Instant now);

    @Query("""
            select count(r) from FlashcardReview r
            where r.userId = :userId and r.status = com.englow3.flashcard.entity.FlashcardReviewStatus.MASTERED
              and r.flashcardId in (select c.id from Flashcard c where c.flashcardSetId = :setId)
            """)
    long countMasteredInSet(@Param("userId") UUID userId, @Param("setId") UUID setId);

    /**
     * Due and mastered for a page of sets in one query, as {@code [setId, due, mastered]} rows. One pair of counts per
     * set made a page of twenty sets into forty round trips.
     */
    @Query("""
            select c.flashcardSetId,
                   sum(case when r.dueAt <= :now then 1 else 0 end),
                   sum(case when r.status = com.englow3.flashcard.entity.FlashcardReviewStatus.MASTERED then 1 else 0 end)
            from FlashcardReview r, Flashcard c
            where r.flashcardId = c.id and r.userId = :userId and c.flashcardSetId in :setIds
            group by c.flashcardSetId
            """)
    List<Object[]> countDueAndMasteredBySetRaw(@Param("userId") UUID userId, @Param("setIds") Collection<UUID> setIds,
            @Param("now") Instant now);

    /** Sets the learner has no reviews in are absent; read them as zero. */
    default Map<UUID, long[]> countDueAndMasteredBySet(UUID userId, Collection<UUID> setIds, Instant now) {
        if (setIds.isEmpty()) {
            return Map.of();
        }
        return countDueAndMasteredBySetRaw(userId, setIds, now).stream().collect(Collectors.toMap(row -> (UUID) row[0],
                row -> new long[] { ((Number) row[1]).longValue(), ((Number) row[2]).longValue() }));
    }

    /**
     * The learner's due cards in one set, in the set's order, with their reviews, as
     * {@code [Flashcard, FlashcardReview]} rows - only as many as a session takes, rather than every card in the set to
     * pick twenty from.
     */
    @Query("""
            select c, r from Flashcard c, FlashcardReview r
            where r.flashcardId = c.id and c.flashcardSetId = :setId and r.userId = :userId and r.dueAt <= :now
            order by c.orderNo
            """)
    List<Object[]> findDueCardsInSet(@Param("userId") UUID userId, @Param("setId") UUID setId,
            @Param("now") Instant now, Limit limit);
}
