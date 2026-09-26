package com.englow3.flashcard.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.FlashcardSetStatus;

public interface FlashcardSetRepository extends JpaRepository<FlashcardSet, UUID> {

    /** The slug is the set's public handle, so it has to be unique across every status, not just published ones. */
    boolean existsBySlug(String slug);

    Optional<FlashcardSet> findBySlugAndStatus(String slug, FlashcardSetStatus status);

    @Query("""
            select s from FlashcardSet s
            where s.status = :status
              and (:topic is null or s.topic = :topic)
              and (:title is null or lower(s.name) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<FlashcardSet> searchByStatus(@Param("status") FlashcardSetStatus status, @Param("topic") String topic,
            @Param("title") String title, Pageable pageable);

    /**
     * The authoring list, which unlike the catalogue must show every status - an administrator with no way to see a
     * draft has no way to review one. A null status means "all", so one query serves both the full list and the review
     * queue.
     */
    @Query("""
            select c from FlashcardSet c
            where (:status is null or c.status = :status)
              and (:title is null or lower(c.name) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<FlashcardSet> searchForAuthoring(@Param("status") FlashcardSetStatus status, @Param("title") String title,
            Pageable pageable);
}
