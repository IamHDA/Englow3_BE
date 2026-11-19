package com.englow3.chat.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.chat.entity.ChatSocketTicketRecord;

public interface ChatSocketTicketRepository extends JpaRepository<ChatSocketTicketRecord, String> {

    /**
     * Single use: only the first redemption of an unexpired ticket returns its user, and it does so from one statement,
     * so two handshakes presenting the same ticket at once cannot both win, on this instance or another. Writes, so it
     * must run inside the caller's read-write transaction.
     */
    @Query(value = """
            update chat_socket_tickets set used_at = :now
            where token_hash = :hash and used_at is null and expires_at > :now
            returning user_id
            """, nativeQuery = true)
    Optional<UUID> redeem(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query(value = "delete from chat_socket_tickets where expires_at < :before", nativeQuery = true)
    int deleteExpiredBefore(@Param("before") Instant before);
}
