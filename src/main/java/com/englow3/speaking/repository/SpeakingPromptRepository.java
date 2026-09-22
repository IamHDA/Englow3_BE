package com.englow3.speaking.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.entity.SpeakingPromptStatus;

public interface SpeakingPromptRepository extends JpaRepository<SpeakingPrompt, UUID> {

    /** The slug is the prompt's public handle, so it is unique across every status, not just published ones. */
    boolean existsBySlug(String slug);

    @Query("""
            select p from SpeakingPrompt p
            where p.status = :status
              and (:category is null or p.category = :category)
              and (:title is null or lower(p.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<SpeakingPrompt> searchByStatus(@Param("status") SpeakingPromptStatus status,
            @Param("category") String category, @Param("title") String title, Pageable pageable);

    /**
     * The authoring list, which unlike the catalogue must show every status - an administrator with no way to see a
     * draft has no way to review one. A null status means "all".
     */
    @Query("""
            select p from SpeakingPrompt p
            where (:status is null or p.status = :status)
              and (:title is null or lower(p.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<SpeakingPrompt> searchForAuthoring(@Param("status") SpeakingPromptStatus status, @Param("title") String title,
            Pageable pageable);
}
