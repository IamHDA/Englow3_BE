package com.englow3.chat.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.chat.entity.ChatParticipant;
import com.englow3.chat.entity.ChatParticipantId;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipantId> {

    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    /** The other person of a direct conversation; empty once their account is erased. */
    @Query("select p.id.userId from ChatParticipant p where p.id.conversationId = :c and p.id.userId <> :me")
    Optional<UUID> findOtherParticipant(@Param("c") UUID conversationId, @Param("me") UUID me);

    @Modifying
    @Query(value = """
            insert into chat_participants (conversation_id, user_id, last_read_seq, joined_at)
            values (:c, :u, 0, :now) on conflict do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("c") UUID conversationId, @Param("u") UUID userId, @Param("now") Instant now);

    /**
     * Never moves backwards and never past the conversation's last seq. Returns the mark now stored, or empty when the
     * person is not in the conversation. Writes, so it must run inside the caller's read-write transaction.
     */
    @Query(value = """
            update chat_participants p
            set last_read_seq = greatest(p.last_read_seq, least(:seq, c.last_seq))
            from chat_conversations c
            where c.id = p.conversation_id and p.conversation_id = :c and p.user_id = :u
            returning p.last_read_seq
            """, nativeQuery = true)
    Optional<Long> advanceReadMark(@Param("c") UUID conversationId, @Param("u") UUID userId, @Param("seq") long seq);

    /** Empty when the person is not in the conversation. */
    @Query("select p.lastReadSeq from ChatParticipant p where p.id.conversationId = :c and p.id.userId = :u")
    Optional<Long> findReadMark(@Param("c") UUID conversationId, @Param("u") UUID userId);
}
