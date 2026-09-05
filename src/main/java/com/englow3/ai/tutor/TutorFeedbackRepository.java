package com.englow3.ai.tutor;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TutorFeedbackRepository extends JpaRepository<TutorFeedback, UUID> {

    Optional<TutorFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);
}
