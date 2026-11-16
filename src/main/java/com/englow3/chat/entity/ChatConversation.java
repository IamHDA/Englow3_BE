package com.englow3.chat.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A one-to-one conversation. It holds no user columns: who is in it, and how far each has read, are participant rows.
 * <p>
 * The one rule here is the sequence. {@code seq} is handed out per conversation and only ever by a caller holding the
 * row's {@code FOR UPDATE} lock until commit, which is what makes seq order the commit order - a reader that has seen
 * seq 7 has seen every message below it. Rows are created by a native {@code insert ... on conflict do nothing}, which
 * is how two friends opening a chat at the same moment end up in one conversation, so this class is only ever loaded.
 */
@Entity
@Table(name = "chat_conversations")
@Getter
public class ChatConversation {

    @Id
    private UUID id;

    @Column(name = "direct_key", nullable = false, updatable = false)
    private String directKey;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatConversation() {
    }

    /**
     * The key of the pair, the same whichever side is asked first: the smaller id, a colon, the larger. The ids are
     * compared as the strings they are stored as, so the order is the one the database would give them too.
     */
    public static String directKey(UUID a, UUID b) {
        if (a.equals(b)) {
            // Cannot happen through the API: a conversation needs two friends, and nobody befriends themselves.
            throw new IllegalArgumentException("A direct conversation needs two different people");
        }
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0 ? first + ":" + second : second + ":" + first;
    }

    /** Hands out the next seq. Call only on a row locked for update, so seq order is commit order. */
    public long nextSeq(Instant now) {
        lastSeq += 1;
        lastMessageAt = now;
        return lastSeq;
    }
}
