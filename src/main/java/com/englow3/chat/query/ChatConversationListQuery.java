package com.englow3.chat.query;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.chat.dto.projection.ConversationRow;
import com.englow3.chat.entity.ChatMessage;
import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/**
 * Read model for the conversation list: each conversation of one person with the other participant and the last message
 * beside it, newest first. SQL rather than JPA because it is one join across three tables that no entity matches, and
 * the page must not cost a query per conversation.
 * <p>
 * Only conversations that have a message are listed: opening a chat and never writing in it leaves nothing to show.
 * Reads only; the caller names the other participants' profiles in one call afterwards.
 */
@Component
@RequiredArgsConstructor
public class ChatConversationListQuery {

    private final JdbcClient jdbcClient;

    /** The newest conversations first; ties on the time resolve by id so a page never repeats or skips a row. */
    public List<ConversationRow> list(UUID me, int limit, long offset) {
        return jdbcClient.sql("""
                select c.id, c.last_seq, c.last_message_at, me.last_read_seq,
                       other.user_id as other_user_id,
                       m.seq as last_message_seq,
                       m.sender_id as last_sender_id,
                       left(m.body, :previewLength) as last_body_preview,
                       (m.image_object_key is not null) as last_has_image,
                       (m.recalled_at is not null) as last_recalled,
                       m.sent_at as last_sent_at
                  from chat_participants me
                  join chat_conversations c on c.id = me.conversation_id
                  left join chat_participants other on other.conversation_id = c.id and other.user_id <> me.user_id
                  left join chat_messages m on m.conversation_id = c.id and m.seq = c.last_seq
                 where me.user_id = :me and c.last_seq > 0
                 order by c.last_message_at desc, c.id
                 limit :limit offset :offset
                """).param("previewLength", ChatMessage.PREVIEW_LENGTH).param("me", me).param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new ConversationRow(rs.getObject("id", UUID.class), rs.getLong("last_seq"),
                        SqlTime.read(rs, "last_message_at"), rs.getLong("last_read_seq"),
                        rs.getObject("other_user_id", UUID.class), rs.getObject("last_message_seq", Long.class),
                        rs.getObject("last_sender_id", UUID.class), rs.getString("last_body_preview"),
                        rs.getBoolean("last_has_image"), rs.getBoolean("last_recalled"),
                        SqlTime.read(rs, "last_sent_at")))
                .list();
    }

    /** How many conversations {@link #list} would return in all, for the page's total. */
    public long count(UUID me) {
        return jdbcClient.sql("""
                select count(*)
                  from chat_participants me
                  join chat_conversations c on c.id = me.conversation_id
                 where me.user_id = :me and c.last_seq > 0
                """).param("me", me).query(Long.class).single();
    }

    /** How many conversations hold something {@code me} has not read: the number on the badge, not a message count. */
    public long unreadConversationCount(UUID me) {
        return jdbcClient.sql("""
                select count(*)
                  from chat_participants me
                  join chat_conversations c on c.id = me.conversation_id
                 where me.user_id = :me and c.last_seq > me.last_read_seq
                """).param("me", me).query(Long.class).single();
    }
}
