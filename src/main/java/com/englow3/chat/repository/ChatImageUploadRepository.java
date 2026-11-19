package com.englow3.chat.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.chat.entity.ChatImageUpload;

public interface ChatImageUploadRepository extends JpaRepository<ChatImageUpload, UUID> {

    Optional<ChatImageUpload> findByIdAndUploaderIdAndConversationId(UUID id, UUID uploaderId, UUID conversationId);

    List<ChatImageUpload> findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(Instant before);

    /** 1 when this call removed the row. Whoever gets 1 owns the object: a send consuming it, or the cleanup job. */
    @Modifying
    @Query("delete from ChatImageUpload u where u.id = :id")
    int deleteByIdReturningCount(@Param("id") UUID id);
}
