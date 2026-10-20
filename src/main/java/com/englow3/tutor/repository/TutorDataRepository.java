package com.englow3.tutor.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

/** Owner-scoped Tutor data only. Queue content is erased through the owning AI API. */
@Repository
@RequiredArgsConstructor
public class TutorDataRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public boolean deleted(UUID user, UUID clientId) {
        return clientId != null && jdbc.sql(
                "select exists(select 1 from tutor_deleted_requests where user_id=:user and client_message_id=:key)")
                .param("user", user).param("key", clientId).query(Boolean.class).single();
    }

    public void expireDeliveryTombstones() {
        jdbc.sql("delete from tutor_deleted_requests where deleted_at < now() - interval '30 days'").update();
    }

    public List<JsonNode> export(UUID user, String kind, int page) {
        String select = switch (kind) {
            case "CONVERSATIONS" ->
                    "select to_jsonb(c)::text as data from tutor_conversations c where c.user_id=:user order by c.id";
            case "MESSAGES" ->
                    "select to_jsonb(m)::text as data from tutor_messages m join tutor_conversations c on c.id=m.tutor_conversation_id where c.user_id=:user order by m.id";
            case "VERSIONS" ->
                    "select to_jsonb(v)::text as data from tutor_reply_versions v join tutor_messages m on m.id=v.message_id join tutor_conversations c on c.id=m.tutor_conversation_id where c.user_id=:user order by v.id";
            // Reviewer notes and assignee identity are not part of the learner's export.
            case "REPORTS" ->
                    "select jsonb_build_object('id',r.id,'messageId',r.message_id,'revision',r.revision,'note',r.learner_note,'status',r.status,'createdAt',r.created_at,'resolvedAt',r.resolved_at)::text as data from tutor_reports r where r.reporter_id=:user order by r.id";
            case "MEMORY" ->
                    "select to_jsonb(mem)::text as data from tutor_memories mem join tutor_conversations c on c.id=mem.conversation_id where c.user_id=:user order by mem.conversation_id";
            case "RECORDINGS" ->
                    "select jsonb_build_object('id',r.id,'locale',r.locale,'status',r.status,'transcript',r.transcript,'createdAt',r.created_at)::text as data from tutor_recordings r where r.user_id=:user and r.status <> 'DELETED' order by r.id";
            default -> throw new BadRequestException("TUTOR_EXPORT_KIND", "Unknown export section");
        };
        return jdbc.sql(select + " limit 3 offset :offset").param("user", user).param("offset", page * 2L)
                .query(String.class).list().stream().map(value -> {
                    try {
                        return mapper.readTree(value);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
                        throw new IllegalStateException(impossible);
                    }
                }).toList();
    }

    public void purge(UUID user) {
        jdbc.sql(
                "insert into tutor_deleted_requests(user_id,client_message_id) select user_id,client_message_id from tutor_requests where user_id=:user on conflict do nothing")
                .param("user", user).update();
        // Preserve the audit action/time, erase free-text support notes tied to the removed reports.
        jdbc.sql(
                "update tutor_audit set note=null where target_id in (select id from tutor_reports where reporter_id=:user)")
                .param("user", user).update();
        jdbc.sql("update tutor_recordings set status='DELETED',transcript=null,error_code=null where user_id=:user")
                .param("user", user).update();
        jdbc.sql("delete from tutor_conversations where user_id=:user").param("user", user).update();
    }
}
