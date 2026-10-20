package com.englow3.tutor.repository;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.englow3.tutor.entity.TutorRecording;

public interface TutorRecordingRepository extends JpaRepository<TutorRecording, UUID> {
    Optional<TutorRecording> findByUserIdAndClientMessageId(UUID userId, UUID clientMessageId);

    List<TutorRecording> findByStatusOrderByCreatedAtAsc(String status, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TutorRecording r where r.id=:id")
    Optional<TutorRecording> lockById(@Param("id") UUID id);

    @Query("select r from TutorRecording r where r.status='DELETED' or r.createdAt < :expired or (r.status='UPLOADING' and r.createdAt < :stalled) order by r.createdAt")
    List<TutorRecording> cleanupCandidates(@Param("expired") Instant expired, @Param("stalled") Instant stalled,
            Pageable page);
}
