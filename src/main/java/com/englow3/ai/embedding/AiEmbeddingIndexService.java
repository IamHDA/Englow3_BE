package com.englow3.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.foundation.AiEmbeddingClient;
import com.englow3.ai.foundation.AiProperties;
import com.englow3.ai.foundation.AiProviderException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class AiEmbeddingIndexService {

    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties properties;

    public AiEmbeddingIndexService(JdbcTemplate jdbcTemplate, AiProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public void enqueue(JsonNode entities, int revision) {
        for (JsonNode entity : entities) {
            Content content = currentContent(entity.path("entityType").asText(), entity.path("entityId").asText());
            if (content == null || !content.approved()) {
                throw new IllegalStateException("Only approved published content can be queued for embedding");
            }
            enqueue(content, revision);
        }
    }

    @Transactional
    public void archive(JsonNode entities) {
        for (JsonNode entity : entities) {
            jdbcTemplate.update("""
                    update ai_embedding_index_state set status = 'STALE', updated_at = now()
                    where content_type = ? and content_id = ? and status <> 'STALE'
                    """, entity.path("entityType").asText(), entity.path("entityId").asText());
        }
    }

    @Transactional
    int reindex(String contentType, String contentId) {
        List<Content> contents = approvedContent(contentType, contentId);
        if (contentId != null && contents.isEmpty()) {
            throw new NotFoundException("AI_EMBEDDING_CONTENT_NOT_FOUND",
                    "Approved content for embedding was not found");
        }
        contents.forEach(content -> enqueue(content, content.revision()));
        return contents.size();
    }

    @Transactional(readOnly = true)
    List<AiEmbeddingIndexDtos.StateResponse> states(String status) {
        if (status != null && !List.of("PENDING", "PROCESSING", "INDEXED", "FAILED", "STALE")
                .contains(status.toUpperCase(java.util.Locale.ROOT))) {
            throw new BadRequestException("AI_EMBEDDING_STATUS_INVALID", "Unknown embedding index status");
        }
        String sql = """
                select content_type, content_id, revision, content_hash, status, attempt_count,
                       provider, model, dimensions, error_code, updated_at, indexed_at
                from ai_embedding_index_state
                """ + (status == null ? "" : " where status = ?")
                + " order by updated_at desc, content_type, content_id limit 500";
        return (status == null ? jdbcTemplate.query(sql, this::mapState)
                : jdbcTemplate.query(sql, this::mapState, status.toUpperCase(java.util.Locale.ROOT)));
    }

    @Transactional
    ClaimedEmbedding claimNext(String workerId) {
        return jdbcTemplate.query("""
                with candidate as (
                    select content_type, content_id, revision, content_hash
                    from ai_embedding_index_state
                    where status in ('PENDING', 'FAILED') and available_at <= now()
                      and attempt_count < ?
                    order by available_at, created_at
                    for update skip locked limit 1
                )
                update ai_embedding_index_state s
                set status = 'PROCESSING', attempt_count = attempt_count + 1,
                    locked_at = now(), locked_by = ?, updated_at = now(),
                    error_code = null, error_message = null
                from candidate c
                where s.content_type = c.content_type and s.content_id = c.content_id
                  and s.revision = c.revision and s.content_hash = c.content_hash
                returning s.content_type, s.content_id, s.revision, s.content_hash, s.attempt_count
                """,
                rs -> rs.next()
                        ? new ClaimedEmbedding(rs.getString("content_type"), rs.getString("content_id"),
                                rs.getInt("revision"), rs.getString("content_hash"), rs.getInt("attempt_count"))
                        : null,
                MAX_ATTEMPTS, workerId);
    }

    @Transactional
    void complete(ClaimedEmbedding claim, AiEmbeddingClient.Result result) {
        Content current = currentContent(claim.contentType(), claim.contentId());
        if (current == null || !current.approved() || !sha256(current.text()).equals(claim.contentHash())) {
            stale(claim);
            if (current != null && current.approved()) {
                enqueue(current, Math.max(current.revision(), claim.revision()));
            }
            return;
        }
        if (result.embedding().size() != AiEmbeddingClient.DIMENSIONS) {
            throw new AiProviderException("AI_EMBEDDING_DIMENSION_MISMATCH",
                    "Embedding dimensions do not match the database vector contract", false);
        }
        String vector = vectorLiteral(result.embedding());
        int updated = updateDomainVector(claim.contentType(), claim.contentId(), claim.contentHash(), vector);
        if (updated != 1) {
            stale(claim);
            return;
        }
        jdbcTemplate.update("""
                update ai_embedding_index_state
                set status = 'INDEXED', provider = ?, model = ?, dimensions = ?, input_tokens = ?,
                    locked_at = null, locked_by = null, indexed_at = now(), updated_at = now()
                where content_type = ? and content_id = ? and revision = ? and content_hash = ?
                  and status = 'PROCESSING'
                """, properties.provider(), result.model(), result.embedding().size(), result.inputTokens(),
                claim.contentType(), claim.contentId(), claim.revision(), claim.contentHash());
    }

    @Transactional
    void fail(ClaimedEmbedding claim, String code, String message, boolean retryable) {
        boolean retry = retryable && claim.attemptCount() < MAX_ATTEMPTS;
        long backoffSeconds = Math.min(300, 5L << Math.max(0, claim.attemptCount() - 1));
        jdbcTemplate.update("""
                update ai_embedding_index_state
                set status = 'FAILED', error_code = ?, error_message = ?, locked_at = null, locked_by = null,
                    available_at = ?, updated_at = now(),
                    attempt_count = case when ? then attempt_count else ? end
                where content_type = ? and content_id = ? and revision = ? and content_hash = ?
                  and status = 'PROCESSING'
                """, code, truncate(message), java.sql.Timestamp.from(Instant.now().plusSeconds(backoffSeconds)), retry,
                MAX_ATTEMPTS, claim.contentType(), claim.contentId(), claim.revision(), claim.contentHash());
    }

    @Transactional
    void recoverStale(Duration timeout) {
        jdbcTemplate.update("""
                update ai_embedding_index_state
                set status = 'FAILED', error_code = 'AI_EMBEDDING_LOCK_TIMEOUT',
                    error_message = 'Embedding worker lock expired', locked_at = null, locked_by = null,
                    available_at = now(), updated_at = now()
                where status = 'PROCESSING' and locked_at < ?
                """, java.sql.Timestamp.from(Instant.now().minus(timeout)));
    }

    Content currentContent(String type, String id) {
        return switch (type) {
            case "EXAM_ITEM" -> one(type, id, "exam_items", "item_id");
            case "SHADOWING_CLIP" -> one(type, id, "shadowing_clips", "clip_id");
            case "FLASHCARD" -> one(type, id, "flashcards", "id");
            case "GRAMMAR_POINT" -> one(type, id, "grammar_points", "id");
            default -> null;
        };
    }

    static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void enqueue(Content content, int revision) {
        String hash = sha256(content.text());
        jdbcTemplate.update("""
                update ai_embedding_index_state set status = 'STALE', updated_at = now()
                where content_type = ? and content_id = ? and revision <= ? and content_hash <> ?
                  and status in ('PENDING', 'FAILED', 'INDEXED')
                """, content.type(), content.id(), revision, hash);
        jdbcTemplate.update("""
                insert into ai_embedding_index_state
                    (content_type, content_id, revision, content_hash, status)
                values (?, ?, ?, ?, 'PENDING')
                on conflict (content_type, content_id, revision, content_hash) do update
                set status = 'PENDING', attempt_count = 0, available_at = now(), locked_at = null,
                    locked_by = null, error_code = null, error_message = null, updated_at = now()
                """, content.type(), content.id(), revision, hash);
    }

    private List<Content> approvedContent(String type, String id) {
        String filterType = type == null ? null : type.toUpperCase(java.util.Locale.ROOT);
        return jdbcTemplate
                .query("""
                        with content as (
                            select 'EXAM_ITEM' type, item_id id, embedding_text, review_status from exam_items
                            union all select 'SHADOWING_CLIP', clip_id, embedding_text, review_status from shadowing_clips
                            union all select 'FLASHCARD', id, embedding_text, review_status from flashcards
                            union all select 'GRAMMAR_POINT', id, embedding_text, review_status from grammar_points
                        )
                        select c.type, c.id, c.embedding_text,
                               coalesce((select max(p.revision) from ai_content_publications p
                                         where p.entity_type = c.type and p.entity_id = c.id), 0) revision
                        from content c
                        where c.review_status = 'human_approved'
                          and (?::text is null or c.type = ?) and (?::text is null or c.id = ?)
                        order by c.type, c.id
                        """,
                        (rs, row) -> new Content(rs.getString("type"), rs.getString("id"),
                                rs.getString("embedding_text"), true, rs.getInt("revision")),
                        filterType, filterType, id, id);
    }

    private Content one(String type, String id, String table, String idColumn) {
        String sql = "select embedding_text, review_status, coalesce((select max(revision)"
                + " from ai_content_publications where entity_type = ? and entity_id = ?), 0) revision from " + table
                + " where " + idColumn + " = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new Content(type, id, rs.getString("embedding_text"),
                    "human_approved".equals(rs.getString("review_status")), rs.getInt("revision"));
        }, type, id, id);
    }

    private int updateDomainVector(String type, String id, String expectedHash, String vector) {
        String[] target = switch (type) {
            case "EXAM_ITEM" -> new String[] { "exam_items", "item_id" };
            case "SHADOWING_CLIP" -> new String[] { "shadowing_clips", "clip_id" };
            case "FLASHCARD" -> new String[] { "flashcards", "id" };
            case "GRAMMAR_POINT" -> new String[] { "grammar_points", "id" };
            default -> throw new IllegalArgumentException("Unknown embedding content type");
        };
        String sql = "update " + target[0] + " set embedding = cast(? as vector) where " + target[1]
                + " = ? and review_status = 'human_approved'"
                + " and encode(sha256(convert_to(embedding_text, 'UTF8')), 'hex') = ?";
        return jdbcTemplate.update(sql, vector, id, expectedHash);
    }

    private void stale(ClaimedEmbedding claim) {
        jdbcTemplate.update("""
                update ai_embedding_index_state
                set status = 'STALE', locked_at = null, locked_by = null, updated_at = now()
                where content_type = ? and content_id = ? and revision = ? and content_hash = ?
                """, claim.contentType(), claim.contentId(), claim.revision(), claim.contentHash());
    }

    private String vectorLiteral(List<Double> vector) {
        return vector.stream().map(String::valueOf).reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]").orElseThrow();
    }

    private AiEmbeddingIndexDtos.StateResponse mapState(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        java.sql.Timestamp indexedAt = rs.getTimestamp("indexed_at");
        return new AiEmbeddingIndexDtos.StateResponse(rs.getString("content_type"), rs.getString("content_id"),
                rs.getInt("revision"), rs.getString("content_hash"), rs.getString("status"), rs.getInt("attempt_count"),
                rs.getString("provider"), rs.getString("model"), rs.getObject("dimensions", Integer.class),
                rs.getString("error_code"), rs.getTimestamp("updated_at").toInstant(),
                indexedAt == null ? null : indexedAt.toInstant());
    }

    private String truncate(String message) {
        if (message == null) {
            return "Embedding generation failed";
        }
        return message.substring(0, Math.min(500, message.length()));
    }

    record Content(String type, String id, String text, boolean approved, int revision) {
    }

    record ClaimedEmbedding(String contentType, String contentId, int revision, String contentHash, int attemptCount) {
    }
}
