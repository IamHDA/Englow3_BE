package com.englow3.chat.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.chat.entity.ChatConversation;

import jakarta.persistence.LockModeType;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    /** Held to the end of the transaction: this is what makes seq order the commit order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id")
    Optional<ChatConversation> findByIdForUpdate(@Param("id") UUID id);

    Optional<ChatConversation> findByDirectKey(String directKey);

    /** 1 when this call created it, 0 when the pair already had one. Concurrent callers wait on the unique index. */
    @Modifying
    @Query(value = """
            insert into chat_conversations (id, direct_key, last_seq, created_at) values (:id, :key, 0, :now)
            on conflict (direct_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("key") String directKey, @Param("now") Instant now);

    /** Conversations nobody is left in (both accounts erased). */
    @Query(value = """
            select c.id from chat_conversations c
            where not exists (select 1 from chat_participants p where p.conversation_id = c.id)
            limit 100
            """, nativeQuery = true)
    List<UUID> findEmpty();

    /**
     * Removes those of {@code ids} that still have no participant, and returns which it removed (messages and uploads
     * go by cascade). Conditional: two sweeps at once cannot both claim a conversation - the second waits for the
     * first's delete to commit, finds the row gone and removes nothing - so neither fails and only the one that removed
     * it deletes its images. Writes, so it must run inside the caller's read-write transaction.
     */
    @Query(value = """
            delete from chat_conversations c
            where c.id in (:ids)
              and not exists (select 1 from chat_participants p where p.conversation_id = c.id)
            returning c.id
            """, nativeQuery = true)
    List<UUID> deleteIfEmpty(@Param("ids") Collection<UUID> ids);
}
