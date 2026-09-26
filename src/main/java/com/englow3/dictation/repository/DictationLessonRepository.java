package com.englow3.dictation.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.dictation.entity.DictationLesson;
import com.englow3.dictation.entity.DictationLessonStatus;

public interface DictationLessonRepository extends JpaRepository<DictationLesson, UUID> {

    boolean existsBySlug(String slug);

    @Query("""
            select l from DictationLesson l
            where l.status = :status
              and (:topic is null or l.topic = :topic)
              and (:title is null or lower(l.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<DictationLesson> searchByStatus(@Param("status") DictationLessonStatus status, @Param("topic") String topic,
            @Param("title") String title, Pageable pageable);

    /**
     * The authoring list, which unlike the catalogue must show every status - an administrator with no way to see a
     * draft has no way to review one. A null status means "all", so one query serves both the full list and the review
     * queue.
     */
    @Query("""
            select c from DictationLesson c
            where (:status is null or c.status = :status)
              and (:title is null or lower(c.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<DictationLesson> searchForAuthoring(@Param("status") DictationLessonStatus status,
            @Param("title") String title, Pageable pageable);
}
