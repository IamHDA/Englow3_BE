package com.englow3.learning.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.DictationLesson;
import com.englow3.learning.entity.DictationLessonStatus;

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
}
