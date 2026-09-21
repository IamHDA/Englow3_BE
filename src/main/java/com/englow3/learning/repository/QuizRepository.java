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
}
