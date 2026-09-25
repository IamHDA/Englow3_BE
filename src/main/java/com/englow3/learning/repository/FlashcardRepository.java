package com.englow3.learning.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.Flashcard;

public interface FlashcardRepository extends JpaRepository<Flashcard, UUID> {

    List<Flashcard> findByFlashcardSetIdOrderByOrderNo(UUID flashcardSetId);

    long countByFlashcardSetId(UUID flashcardSetId);

    /** Empty for a set with no cards yet, which is why it is an Optional rather than a zero. */
    @Query("select max(c.orderNo) from Flashcard c where c.flashcardSetId = :setId")
    Optional<Integer> findMaxOrderNo(@Param("setId") UUID setId);

    /**
     * Counts for a page of parents in one query. One count per row would make a page of twenty into twenty-one round
     * trips to fill a single column.
     */
    @Query("""
            select c.flashcardSetId, count(c) from Flashcard c
            where c.flashcardSetId in :setIds
            group by c.flashcardSetId
            """)
    List<Object[]> countBySetIdsRaw(@Param("setIds") Collection<UUID> setIds);

    default Map<UUID, Long> countBySetIds(Collection<UUID> setIds) {
        if (setIds.isEmpty()) {
            return Map.of();
        }
        return countBySetIdsRaw(setIds).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    /** Cards in a set the learner has never reviewed, in the set's order, only as many as asked for. */
    @Query("""
            select c from Flashcard c
            where c.flashcardSetId = :setId
              and not exists (select r.id from FlashcardReview r where r.flashcardId = c.id and r.userId = :userId)
            order by c.orderNo
            """)
    List<Flashcard> findUnseenInSet(@Param("userId") UUID userId, @Param("setId") UUID setId, Limit limit);
}
