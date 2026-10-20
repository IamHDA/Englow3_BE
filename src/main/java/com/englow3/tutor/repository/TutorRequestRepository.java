package com.englow3.tutor.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.tutor.entity.TutorRequest;

public interface TutorRequestRepository extends JpaRepository<TutorRequest, UUID> {
    Optional<TutorRequest> findByUserIdAndClientMessageId(UUID userId, UUID clientMessageId);
}
