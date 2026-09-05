package com.englow3.ai.tutor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class TutorGroundingService implements TutorRetrievalPort {

    private static final int CANDIDATE_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;
    private final TutorEmbeddingClient embeddingClient;
    private final PromptInjectionDetector injectionDetector;

    TutorGroundingService(JdbcTemplate jdbcTemplate, TutorEmbeddingClient embeddingClient,
            PromptInjectionDetector injectionDetector) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient;
        this.injectionDetector = injectionDetector;
    }

    @Override
    public RetrievalResult retrieve(UUID userId, String query, int limit) {
        String queryVector = embeddingClient.embed(query).map(this::vectorLiteral).orElse(null);
        List<GroundingReference> candidates = jdbcTemplate.query("""
                with corpus as (
                    select 'FLASHCARD' content_type, f.id content_id,
                           coalesce((select max(p.revision) from ai_content_publications p
                                     where p.entity_type = 'FLASHCARD' and p.entity_id = f.id), 0) revision,
                           f.cefr_level content_level, 'PUBLIC' access_scope,
                           f.lemma label, f.embedding_text body, f.embedding
                    from flashcards f
                    where f.review_status in ('approved', 'published', 'human_verified', 'human_approved')
                    union all
                    select 'GRAMMAR_POINT', g.id,
                           coalesce((select max(p.revision) from ai_content_publications p
                                     where p.entity_type = 'GRAMMAR_POINT' and p.entity_id = g.id), 0),
                           g.cefr_level, 'PUBLIC', g.title_en, g.embedding_text, g.embedding
                    from grammar_points g
                    where g.review_status in ('approved', 'published', 'human_verified', 'human_approved')
                    union all
                    select 'EXAM_ITEM', e.item_id,
                           coalesce((select max(p.revision) from ai_content_publications p
                                     where p.entity_type = 'EXAM_ITEM' and p.entity_id = e.item_id), 0),
                           null::text, 'PUBLIC', coalesce(e.question_text, 'Approved exam item'),
                           e.embedding_text, e.embedding
                    from exam_items e
                    where e.review_status in ('approved', 'published', 'human_verified', 'human_approved')
                ), ranked as (
                    select *, greatest(
                        case when to_tsvector('simple', body || ' ' || label)
                                      @@ plainto_tsquery('simple', ?) then
                            0.60 + 0.25 * ts_rank_cd(to_tsvector('simple', body || ' ' || label),
                                                     plainto_tsquery('simple', ?))
                        else 0 end,
                        case when cast(? as vector) is not null and embedding is not null then
                            0.40 + 0.60 * greatest(0, 1 - (embedding <=> cast(? as vector)))
                        else 0 end
                    ) score
                    from corpus
                )
                select content_type, content_id, revision, content_level, access_scope, label, body, score
                from ranked where score > 0
                order by score desc, content_type, content_id
                limit ?
                """,
                (rs, row) -> reference(rs.getString("content_type"), rs.getString("content_id"), rs.getInt("revision"),
                        rs.getString("content_level"), rs.getString("access_scope"), rs.getString("label"),
                        rs.getString("body"), rs.getDouble("score")),
                query, query, queryVector, queryVector, CANDIDATE_LIMIT);
        boolean injectionDetected = candidates.stream()
                .anyMatch(candidate -> injectionDetector.detected(candidate.text()));
        List<GroundingReference> safe = candidates.stream()
                .filter(candidate -> !injectionDetector.detected(candidate.text())).limit(Math.max(1, limit)).toList();
        return new RetrievalResult(safe, candidates.size(), queryVector != null, injectionDetected);
    }

    private GroundingReference reference(String type, String id, int revision, String level, String accessScope,
            String label, String body, double score) {
        String referenceId = type + ":" + id + ":R" + revision;
        return new GroundingReference(referenceId, type, id, revision, level, accessScope, label, body, sha256(body),
                score);
    }

    private String vectorLiteral(List<Double> vector) {
        return vector.stream().map(String::valueOf).reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]").orElseThrow();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
