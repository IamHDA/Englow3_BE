package com.englow3.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Someone's place in a conversation and how far they have read. A plain row: the rule that the read mark never moves
 * backwards is enforced where the write happens, by {@code greatest(...)} in
 * {@code ChatParticipantRepository.advanceReadMark}, because that is what stays correct when two tabs write at once - a
 * check in Java would compare against a value another transaction is about to change. So there is no setter and the
 * column is not updatable through this entity: the mark is read through here and written only by that statement. Rows
 * are inserted by a native {@code on conflict do nothing}, never saved from here.
 */
@Entity
@Table(name = "chat_participants")
@Getter
public class ChatParticipant {

    @EmbeddedId
    private ChatParticipantId id;

    @Column(name = "last_read_seq", nullable = false, updatable = false)
    private long lastReadSeq;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected ChatParticipant() {
    }
}
