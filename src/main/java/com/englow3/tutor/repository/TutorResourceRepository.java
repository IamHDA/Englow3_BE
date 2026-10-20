package com.englow3.tutor.repository;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.englow3.tutor.entity.TutorResource;

public interface TutorResourceRepository extends JpaRepository<TutorResource, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TutorResource r where r.id = :id")
    Optional<TutorResource> lockById(@Param("id") UUID id);

    @Query("select r from TutorResource r where (:admin = true or r.authorId = :user or r.status = 'PUBLISHED') and (:status = '' or r.status = :status) order by r.id desc")
    Page<TutorResource> search(@Param("user") UUID user, @Param("admin") boolean admin, @Param("status") String status,
            Pageable page);

    @Query("select coalesce(max(r.versionNumber),0) from TutorResource r where r.documentId = :document")
    int maxVersion(@Param("document") UUID document);

    @Modifying
    @Query("update TutorResource r set r.status = 'ARCHIVED', r.rowVersion = r.rowVersion + 1 where r.documentId = :document and r.status = 'PUBLISHED' and r.id <> :id")
    void retirePublished(@Param("document") UUID document, @Param("id") UUID id);

    @Query(value = """
            select * from tutor_resources where status = 'PUBLISHED'
              and to_tsvector('simple', title || ' ' || body) @@ websearch_to_tsquery('simple', :query)
            order by ts_rank(to_tsvector('simple', title || ' ' || body), websearch_to_tsquery('simple', :query)) desc, id
            """, nativeQuery = true)
    List<TutorResource> retrieve(@Param("query") String query, Pageable page);
}
