package com.englow3.learning.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.Quiz;
import com.englow3.learning.entity.QuizStatus;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    boolean existsBySlug(String slug);

    @Query("""
            select q from Quiz q
            where q.status = :status
              and (:category is null or q.category = :category)
              and (:title is null or lower(q.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<Quiz> searchByStatus(@Param("status") QuizStatus status, @Param("category") String category,
            @Param("title") String title, Pageable pageable);

    /**
     * The authoring list, which unlike the catalogue must show every status - an administrator with no way to see a
     * draft has no way to review one. A null status means "all", so one query serves both the full list and the review
     * queue.
     */
    @Query("""
            select c from Quiz c
            where (:status is null or c.status = :status)
              and (:title is null or lower(c.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<Quiz> searchForAuthoring(@Param("status") QuizStatus status, @Param("title") String title, Pageable pageable);
}
