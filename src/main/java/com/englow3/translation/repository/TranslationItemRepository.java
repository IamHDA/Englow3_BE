package com.englow3.translation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.translation.entity.TranslationItem;

public interface TranslationItemRepository extends JpaRepository<TranslationItem, UUID> {

    List<TranslationItem> findByTranslationSetIdOrderByOrderNo(UUID translationSetId);

    long countByTranslationSetId(UUID translationSetId);

    /** Sentence counts for a page of sets in one query, not one per set. */
    @Query("""
            select i.translationSetId as setId, count(i) as total from TranslationItem i
            where i.translationSetId in :setIds group by i.translationSetId
            """)
    List<SetCount> countBySetIds(@Param("setIds") Collection<UUID> setIds);

    interface SetCount {
        UUID getSetId();

        long getTotal();
    }
}
