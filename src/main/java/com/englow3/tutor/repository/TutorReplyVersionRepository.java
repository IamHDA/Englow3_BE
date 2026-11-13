package com.englow3.tutor.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.tutor.entity.TutorReplyVersion;

public interface TutorReplyVersionRepository extends JpaRepository<TutorReplyVersion, UUID> {
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update TutorReplyVersion v set v.correction=:correction where v.messageId=:message and v.content=:content")
    int attachApprovedCorrection(@org.springframework.data.repository.query.Param("message") UUID message,
            @org.springframework.data.repository.query.Param("content") String content,
            @org.springframework.data.repository.query.Param("correction") String correction);

    List<TutorReplyVersion> findByMessageIdOrderByRevision(UUID messageId);
}
