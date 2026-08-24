package com.englow3.ai.tutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TutorConversationRepository extends JpaRepository<TutorConversation, UUID> {

    List<TutorConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<TutorConversation> findByIdAndUserId(UUID id, UUID userId);
}
