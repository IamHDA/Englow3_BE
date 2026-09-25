package com.englow3.learning.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.DictationSentence;

public interface DictationSentenceRepository extends JpaRepository<DictationSentence, UUID> {

    List<DictationSentence> findByDictationLessonIdOrderByOrderNo(UUID dictationLessonId);

    /** Every sentence of a page of lessons in one query, for a list that needs them all. */
    List<DictationSentence> findByDictationLessonIdInOrderByOrderNo(Collection<UUID> dictationLessonIds);

    long countByDictationLessonId(UUID dictationLessonId);

    /**
     * Counts for a page of parents in one query. One count per row would make a page of twenty into twenty-one round
     * trips to fill a single column.
     */
    @Query("""
            select c.dictationLessonId, count(c) from DictationSentence c
            where c.dictationLessonId in :lessonIds
            group by c.dictationLessonId
            """)
    List<Object[]> countByLessonIdsRaw(@Param("lessonIds") Collection<UUID> lessonIds);

    default Map<UUID, Long> countByLessonIds(Collection<UUID> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return countByLessonIdsRaw(lessonIds).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }
}
