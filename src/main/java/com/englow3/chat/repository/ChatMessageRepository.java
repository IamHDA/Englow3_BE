package com.englow3.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Optional<ChatMessage> findBySenderIdAndClientId(UUID senderId, String clientId);

    /** Everything after {@code afterSeq}, oldest first: how a client catches up after reconnecting. */
    List<ChatMessage> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(UUID conversationId, long afterSeq,
            Pageable page);

    /** Everything before {@code beforeSeq}, newest first: how a client scrolls back. */
    List<ChatMessage> findByConversationIdAndSeqLessThanOrderBySeqDesc(UUID conversationId, long beforeSeq,
            Pageable page);

    @Query("select m.imageObjectKey from ChatMessage m where m.conversationId in :ids and m.imageObjectKey is not null")
    List<String> findImageKeys(@Param("ids") Collection<UUID> conversationIds);
}
