package com.englow3.tutor.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.tutor.entity.TutorConversation;

public interface TutorConversationRepository extends JpaRepository<TutorConversation, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from TutorConversation c where c.id = :id and c.userId = :userId")
    Optional<TutorConversation> lockOwned(@org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("""
            select c from TutorConversation c where c.userId = :userId
            and ((:archived = true and c.archivedAt is not null) or (:archived = false and c.archivedAt is null))
            and lower(c.title) like lower(concat('%', :search, '%'))
            order by c.lastMessageAt desc, c.id desc
            """)
    org.springframework.data.domain.Page<TutorConversation> searchOwned(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("search") String search,
            @org.springframework.data.repository.query.Param("archived") boolean archived,
            org.springframework.data.domain.Pageable page);

    /** Scoped to the owner in the query itself, so a conversation id alone is never enough to read one. */
    Optional<TutorConversation> findByIdAndUserId(UUID id, UUID userId);

    List<TutorConversation> findByUserIdAndArchivedAtIsNullOrderByLastMessageAtDesc(UUID userId);
}
