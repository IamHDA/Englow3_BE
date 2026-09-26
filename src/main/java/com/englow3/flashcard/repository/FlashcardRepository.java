package com.englow3.flashcard.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.flashcard.entity.Flashcard;

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
}
