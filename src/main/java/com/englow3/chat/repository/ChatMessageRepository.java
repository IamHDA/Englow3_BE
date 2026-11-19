package com.englow3.chat.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.chat.entity.ChatMessage;

import jakarta.persistence.LockModeType;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    Optional<ChatMessage> findBySenderIdAndClientId(UUID senderId, String clientId);

    /**
     * Held to the end of the transaction. A recall reads the message, changes it and publishes the change, so two
     * recalls at once must take turns: the second waits, then sees the first's result and is refused as already
     * recalled. It must be the first read of the message in the transaction - a row already loaded is not refreshed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChatMessage m where m.id = :id")
    Optional<ChatMessage> findByIdForUpdate(@Param("id") UUID id);

    /** Everything after {@code afterSeq}, oldest first: how a client catches up after reconnecting. */
    List<ChatMessage> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(UUID conversationId, long afterSeq,
            Pageable page);

    /** Everything before {@code beforeSeq}, newest first: how a client scrolls back. */
    List<ChatMessage> findByConversationIdAndSeqLessThanOrderBySeqDesc(UUID conversationId, long beforeSeq,
            Pageable page);

    /** An image's object key and the conversation it belongs to, so the keys of conversations removed can be told. */
    interface ImageKeyRow {
        UUID getConversationId();

        String getImageObjectKey();
    }

    @Query("""
            select m.conversationId as conversationId, m.imageObjectKey as imageObjectKey from ChatMessage m
            where m.conversationId in :ids and m.imageObjectKey is not null
            """)
    List<ImageKeyRow> findImageKeys(@Param("ids") Collection<UUID> conversationIds);
}
