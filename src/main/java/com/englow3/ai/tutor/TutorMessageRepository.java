package com.englow3.ai.tutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TutorMessageRepository extends JpaRepository<TutorMessage, UUID> {

    List<TutorMessage> findByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    List<TutorMessage> findTop12ByConversationIdAndStatusOrderByCreatedAtDesc(UUID conversationId,
            TutorMessageStatus status);

    Optional<TutorMessage> findByIdAndConversationId(UUID id, UUID conversationId);
}
