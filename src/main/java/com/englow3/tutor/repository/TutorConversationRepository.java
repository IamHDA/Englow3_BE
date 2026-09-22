package com.englow3.tutor.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.tutor.entity.TutorConversation;

public interface TutorConversationRepository extends JpaRepository<TutorConversation, UUID> {

    /** Scoped to the owner in the query itself, so a conversation id alone is never enough to read one. */
    Optional<TutorConversation> findByIdAndUserId(UUID id, UUID userId);

    List<TutorConversation> findByUserIdAndArchivedAtIsNullOrderByLastMessageAtDesc(UUID userId);
}
