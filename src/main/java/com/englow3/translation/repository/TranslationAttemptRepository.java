package com.englow3.translation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.translation.entity.TranslationAttempt;

public interface TranslationAttemptRepository extends JpaRepository<TranslationAttempt, UUID> {

    Optional<TranslationAttempt> findByIdAndUserId(UUID id, UUID userId);

    Page<TranslationAttempt> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * The learner's latest attempt at each sentence of a set - what the practice screen shows beside each line. Ties on
     * time are broken by id so the answer is stable.
     */
    @Query(value = """
            select distinct on (a.translation_item_id) a.*
            from translation_attempts a
            where a.user_id = :userId and a.translation_set_id = :setId
            order by a.translation_item_id, a.created_at desc, a.id desc
            """, nativeQuery = true)
    List<TranslationAttempt> latestPerItem(@Param("userId") UUID userId, @Param("setId") UUID setId);

    /** Sentences each set has a graded attempt at, for the learner's progress on a page of sets. */
    @Query("""
            select a.translationSetId as setId, count(distinct a.translationItemId) as total
            from TranslationAttempt a
            where a.userId = :userId and a.translationSetId in :setIds
              and a.status = com.englow3.translation.entity.TranslationAttemptStatus.GRADED
            group by a.translationSetId
            """)
    List<TranslationItemRepository.SetCount> gradedItemsBySet(@Param("userId") UUID userId,
            @Param("setIds") Collection<UUID> setIds);
}
