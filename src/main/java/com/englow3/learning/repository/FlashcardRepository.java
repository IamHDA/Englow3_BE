package com.englow3.learning.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    /** Batched so a page of sets costs one query for all their counts rather than one each. */
    @Query("select c.flashcardSetId, count(c) from Flashcard c where c.flashcardSetId in :setIds group by c.flashcardSetId")
    List<Object[]> countBySetIds(@Param("setIds") Collection<UUID> setIds);
}
