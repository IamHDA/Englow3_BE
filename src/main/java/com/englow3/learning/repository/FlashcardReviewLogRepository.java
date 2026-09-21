package com.englow3.learning.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.FlashcardReviewLog;

public interface FlashcardReviewLogRepository extends JpaRepository<FlashcardReviewLog, UUID> {

    /** Distinct study days, newest first - the raw material for a streak. */
    @Query(value = """
            select distinct date_trunc('day', reviewed_at at time zone 'UTC') as day
              from flashcard_review_logs
             where user_id = :userId and reviewed_at >= :from
             order by day desc
            """, nativeQuery = true)
    List<Instant> findStudyDaysSince(@Param("userId") UUID userId, @Param("from") Instant from);

    long countByUserIdAndReviewedAtGreaterThanEqual(UUID userId, Instant from);

    /** When the learner last touched each of these sets. Batched so a page of sets costs one query, not one each. */
    @Query("""
            select l.flashcardSetId, max(l.reviewedAt) from FlashcardReviewLog l
            where l.userId = :userId and l.flashcardSetId in :setIds
            group by l.flashcardSetId
            """)
    List<Object[]> findLastStudiedAtBySet(@Param("userId") UUID userId, @Param("setIds") Collection<UUID> setIds);
}
