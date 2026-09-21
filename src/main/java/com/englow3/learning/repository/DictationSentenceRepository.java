package com.englow3.learning.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.DictationSentence;

public interface DictationSentenceRepository extends JpaRepository<DictationSentence, UUID> {

    List<DictationSentence> findByDictationLessonIdOrderByOrderNo(UUID dictationLessonId);

    long countByDictationLessonId(UUID dictationLessonId);

    /** Batched so a page of lessons costs one query for all their counts rather than one each. */
    @Query("select s.dictationLessonId, count(s) from DictationSentence s where s.dictationLessonId in :lessonIds group by s.dictationLessonId")
    List<Object[]> countByLessonIds(@Param("lessonIds") Collection<UUID> lessonIds);
}
