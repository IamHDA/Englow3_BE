package com.englow3.tutor.repository;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TutorMemoryRepository {
    private final JdbcClient jdbc;

    public void lock(UUID conversation) {
        jdbc.sql("select cast(pg_advisory_xact_lock(hashtextextended(:key,0)) as text)")
                .param("key", "tutor-memory:" + conversation).query(String.class).single();
    }

    public record Row(String content, int throughOrder, long epoch, UUID pendingMessageId) {
    }

    public Row read(UUID conversation) {
        return jdbc.sql(
                "select content,through_order,epoch,pending_message_id from tutor_memories where conversation_id=:id")
                .param("id", conversation).query((rs, index) -> new Row(rs.getString(1), rs.getInt(2), rs.getLong(3),
                        rs.getObject(4, UUID.class)))
                .optional().orElse(new Row(null, 0, 0, null));
    }

    public long queue(UUID conversation, UUID message, int through) {
        jdbc.sql("insert into tutor_memories(conversation_id) values(:id) on conflict do nothing")
                .param("id", conversation).update();
        return jdbc.sql(
                "update tutor_memories set epoch=epoch+1,pending_message_id=:message,pending_through_order=:through,updated_at=now() where conversation_id=:id returning epoch")
                .param("id", conversation).param("message", message).param("through", through).query(Long.class)
                .single();
    }

    public void complete(UUID message, int revision, long epoch, int through, String content) {
        var conversation = jdbc.sql("select tutor_conversation_id from tutor_messages where id=:id")
                .param("id", message).query(UUID.class).optional();
        if (conversation.isEmpty())
            return;
        lock(conversation.get());
        jdbc.sql(
                """
                        update tutor_memories mem set content=:content,through_order=:through,source_message_id=:message,pending_message_id=null,pending_through_order=0,updated_at=now()
                        where mem.pending_message_id=:message and mem.epoch=:epoch
                        and exists(select 1 from tutor_messages m where m.id=:message and m.tutor_conversation_id=mem.conversation_id and m.revision=:revision and m.status='READY' and m.is_summary=true)
                        """)
                .param("message", message).param("revision", revision).param("epoch", epoch).param("through", through)
                .param("content", content).update();
    }

    public void clear(UUID conversation) {
        lock(conversation);
        jdbc.sql(
                "update tutor_memories set content=null,through_order=0,source_message_id=null,epoch=epoch+1,pending_message_id=null,pending_through_order=0,updated_at=now() where conversation_id=:id")
                .param("id", conversation).update();
    }

    public void invalidateForMessage(UUID message) {
        var conversation = jdbc.sql("select tutor_conversation_id from tutor_messages where id=:id")
                .param("id", message).query(UUID.class).optional();
        if (conversation.isEmpty())
            return;
        lock(conversation.get());
        jdbc.sql(
                """
                        update tutor_memories mem set content=null,through_order=0,source_message_id=null,epoch=epoch+1,pending_message_id=null,pending_through_order=0,updated_at=now()
                        from tutor_messages m where m.id=:message and mem.conversation_id=m.tutor_conversation_id
                        and (m.id=mem.source_message_id or m.order_no<=mem.through_order or m.order_no<=mem.pending_through_order)
                        """)
                .param("message", message).update();
    }
}
