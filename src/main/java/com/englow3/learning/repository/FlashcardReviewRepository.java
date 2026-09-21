package com.englow3.learning.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.FlashcardReview;

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
            where r.userId = :userId and r.status = com.englow3.learning.entity.FlashcardReviewStatus.MASTERED
              and r.flashcardId in (select c.id from Flashcard c where c.flashcardSetId = :setId)
            """)
    long countMasteredInSet(@Param("userId") UUID userId, @Param("setId") UUID setId);
}
