package com.englow3.translation.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.translation.entity.TranslationSet;
import com.englow3.translation.entity.TranslationSetStatus;

public interface TranslationSetRepository extends JpaRepository<TranslationSet, UUID> {
    @Query("select s from TranslationSet s where (:status is null or s.status=:status) and (:title is null or lower(s.title) like lower(concat('%',cast(:title as String),'%'))) and (s.createdByUserId=:author or s.status=com.englow3.translation.entity.TranslationSetStatus.PUBLISHED)")
    Page<TranslationSet> searchVisibleForAuthoring(@Param("status") TranslationSetStatus status,
            @Param("title") String title, @Param("author") UUID author, Pageable page);

    /** The slug is the set's public handle, so it is unique across every status, not just published ones. */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @Query("""
            select s from TranslationSet s
            where s.status = :status
              and (:topic is null or s.topic = :topic)
              and (:title is null or lower(s.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<TranslationSet> searchByStatus(@Param("status") TranslationSetStatus status, @Param("topic") String topic,
            @Param("title") String title, Pageable pageable);

    /** The authoring list, which must show every status. A null status means "all". */
    @Query("""
            select s from TranslationSet s
            where (:status is null or s.status = :status)
              and (:title is null or lower(s.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<TranslationSet> searchForAuthoring(@Param("status") TranslationSetStatus status, @Param("title") String title,
            Pageable pageable);
}
