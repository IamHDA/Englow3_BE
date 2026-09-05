package com.englow3.ai.speaking;

import java.net.URL;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.ai.foundation.RenderedPrompt;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class SpeakingSessionService {

    private static final Map<String, String> CONTENT_TYPES = Map.of("audio/wav; codecs=audio/pcm; samplerate=16000",
            "wav", "audio/ogg; codecs=opus", "ogg");

    private final JdbcTemplate jdbcTemplate;
    private final UserDirectory userDirectory;
    private final ObjectStorageClient storage;
    private final SpeechProperties properties;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;

    SpeakingSessionService(JdbcTemplate jdbcTemplate, UserDirectory userDirectory, ObjectStorageClient storage,
            SpeechProperties properties, AiPromptService promptService, AiJobService jobService,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDirectory = userDirectory;
        this.storage = storage;
        this.properties = properties;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    SpeakingDtos.CreateSessionResponse create(SpeakingDtos.CreateSessionRequest request) {
        UUID userId = requireUserId();
        String extension = CONTENT_TYPES.get(request.contentType());
        if (extension == null) {
            throw new BadRequestException("SPEAKING_AUDIO_TYPE_UNSUPPORTED", "Use WAV PCM 16 kHz or OGG Opus audio");
        }
        String reference = request.referenceText() == null ? null : request.referenceText().strip();
        if (request.mode() == SpeakingDtos.Mode.READ_ALOUD && (reference == null || reference.isBlank())) {
            throw new BadRequestException("SPEAKING_REFERENCE_REQUIRED", "Read-aloud sessions require reference text");
        }
        UUID sessionId = UUID.randomUUID();
        UUID practiceId = request.practiceId() == null ? sessionId : request.practiceId();
        int turnNumber = nextTurn(userId, request.practiceId());
        String bucket = storage.defaultBucket();
        String objectKey = "users/%s/speaking/%s/input.%s".formatted(userId, sessionId, extension);
        Instant now = Instant.now();
        Instant retentionUntil = now.plus(properties.retention());
        jdbcTemplate.update("""
                insert into speaking_sessions
                    (id, user_id, mode, locale, reference_text, audio_bucket, audio_object_key,
                     audio_content_type, status, consented_at, retention_until, practice_id, turn_number)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'AWAITING_UPLOAD', ?, ?, ?, ?)
                """, sessionId, userId, request.mode().name(), properties.locale(), reference, bucket, objectKey,
                request.contentType(), Timestamp.from(now), Timestamp.from(retentionUntil), practiceId, turnNumber);
        URL uploadUrl = storage.presignPut(bucket, objectKey, request.contentType(), properties.uploadUrlTtl());
        return new SpeakingDtos.CreateSessionResponse(sessionId, practiceId, turnNumber, uploadUrl, objectKey,
                now.plus(properties.uploadUrlTtl()), request.contentType());
    }

    @Transactional
    SpeakingDtos.SubmitSessionResponse submit(UUID sessionId, String idempotencyKey) {
        UUID userId = requireUserId();
        SessionUpload upload = lockUpload(sessionId, userId);
        if (upload.jobId() != null && idempotencyKey.equals(upload.submitIdempotencyKey())) {
            return new SpeakingDtos.SubmitSessionResponse(sessionId, upload.jobId(), "EXISTING");
        }
        if (upload.jobId() != null) {
            throw new ConflictException("SPEAKING_SUBMIT_IDEMPOTENCY_CONFLICT",
                    "The speaking session was already submitted with a different idempotency key");
        }
        ObjectStorageClient.StoredObjectMetadata metadata;
        try {
            metadata = storage.metadata(upload.bucket(), upload.objectKey());
        } catch (RuntimeException ex) {
            throw new BadRequestException("SPEAKING_AUDIO_NOT_UPLOADED", "Audio has not been uploaded yet");
        }
        if (metadata.contentLength() <= 0 || metadata.contentLength() > properties.maxAudioBytes()) {
            throw new BadRequestException("SPEAKING_AUDIO_SIZE_INVALID",
                    "Audio file size is outside the allowed limit");
        }
        if (!upload.contentType().equals(metadata.contentType())) {
            throw new BadRequestException("SPEAKING_AUDIO_TYPE_MISMATCH",
                    "Uploaded audio content type does not match the signed request");
        }

        RenderedPrompt prompt = promptService.render("SPEAKING_LANGUAGE_FEEDBACK",
                Map.of("transcript", "__TRANSCRIPT__", "scores", "__SCORES__"));
        ObjectNode payload = objectMapper.createObjectNode().put("sessionId", sessionId.toString())
                .put("systemPrompt", prompt.systemPrompt()).put("userPromptTemplate", prompt.userPrompt());
        AiJob job = jobService.submitForCurrentUser(AiCapability.SPEAKING, "SPEAKING_ASSESSMENT", "SPEAKING_SESSION",
                sessionId, prompt.version(), payload, "SPEAKING:" + idempotencyKey);
        jdbcTemplate.update("""
                update speaking_sessions
                set status = 'PROCESSING', ai_job_id = ?, audio_size_bytes = ?, submitted_at = now(),
                    submit_idempotency_key = ?
                where id = ?
                """, job.getId(), metadata.contentLength(), idempotencyKey, sessionId);
        return new SpeakingDtos.SubmitSessionResponse(sessionId, job.getId(), job.getStatus().name());
    }

    @Transactional(readOnly = true)
    SpeakingDtos.SessionResult result(UUID sessionId) {
        UUID userId = requireUserId();
        SessionResultRow row = jdbcTemplate.query("""
                select s.id, s.mode, s.status, s.retention_until, a.recognized_text, a.accuracy_score,
                       a.fluency_score, a.completeness_score, a.prosody_score, a.pronunciation_score,
                       a.grammar_feedback, a.vocabulary_feedback, a.provider_name, s.audio_deleted_at
                from speaking_sessions s
                left join speaking_assessments a on a.session_id = s.id
                where s.id = ? and s.user_id = ?
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new SessionResultRow(rs.getObject("id", UUID.class), rs.getString("mode"), rs.getString("status"),
                    rs.getString("recognized_text"), decimal(rs.getBigDecimal("accuracy_score")),
                    decimal(rs.getBigDecimal("fluency_score")), decimal(rs.getBigDecimal("completeness_score")),
                    decimal(rs.getBigDecimal("prosody_score")), decimal(rs.getBigDecimal("pronunciation_score")),
                    rs.getString("grammar_feedback"), rs.getString("vocabulary_feedback"),
                    rs.getString("provider_name"), rs.getTimestamp("retention_until").toInstant(),
                    instant(rs.getTimestamp("audio_deleted_at")));
        }, sessionId, userId);
        if (row == null) {
            throw new NotFoundException("SPEAKING_SESSION_NOT_FOUND", "Speaking session was not found");
        }
        return new SpeakingDtos.SessionResult(row.id(), row.mode(), row.status(), row.recognizedText(), row.accuracy(),
                row.fluency(), row.completeness(), row.prosody(), row.pronunciation(), row.grammarFeedback(),
                row.vocabularyFeedback(), row.provider(), wordScores(sessionId), recommendations(sessionId),
                row.retentionUntil(), row.audioDeletedAt());
    }

    @Transactional(readOnly = true)
    List<SpeakingDtos.SessionSummary> history() {
        UUID userId = requireUserId();
        return jdbcTemplate.query("""
                select s.id, s.mode, s.status, s.created_at, s.completed_at, s.retention_until,
                       a.recognized_text, a.pronunciation_score
                from speaking_sessions s
                left join speaking_assessments a on a.session_id = s.id
                where s.user_id = ?
                order by s.created_at desc
                limit 100
                """,
                (rs, row) -> new SpeakingDtos.SessionSummary(rs.getObject("id", UUID.class), rs.getString("mode"),
                        rs.getString("status"), rs.getString("recognized_text"),
                        decimal(rs.getBigDecimal("pronunciation_score")), rs.getTimestamp("created_at").toInstant(),
                        instant(rs.getTimestamp("completed_at")), rs.getTimestamp("retention_until").toInstant()),
                userId);
    }

    @Transactional(readOnly = true)
    List<SpeakingDtos.RecurringError> recurringErrors() {
        UUID userId = requireUserId();
        return jdbcTemplate.query("""
                select unit_type, normalized_unit, error_type, occurrence_count, average_accuracy,
                       first_seen_at, last_seen_at
                from speaking_error_aggregates where user_id = ?
                order by occurrence_count desc, last_seen_at desc limit 100
                """,
                (rs, row) -> new SpeakingDtos.RecurringError(rs.getString("unit_type"), rs.getString("normalized_unit"),
                        rs.getString("error_type"), rs.getInt("occurrence_count"),
                        decimal(rs.getBigDecimal("average_accuracy")), rs.getTimestamp("first_seen_at").toInstant(),
                        rs.getTimestamp("last_seen_at").toInstant()),
                userId);
    }

    @Transactional(readOnly = true)
    SpeakingDtos.ProgressResponse progress(int windowDays) {
        if (windowDays < 1 || windowDays > 365) {
            throw new BadRequestException("SPEAKING_PROGRESS_WINDOW_INVALID",
                    "Progress window must be between 1 and 365 days");
        }
        UUID userId = requireUserId();
        Instant from = Instant.now().minusSeconds(windowDays * 86_400L);
        ProgressRow row = jdbcTemplate.query("""
                select count(*) sessions, avg(a.accuracy_score) accuracy, avg(a.fluency_score) fluency,
                       avg(a.pronunciation_score) pronunciation,
                       (array_agg(a.pronunciation_score order by s.completed_at desc)
                            filter (where a.pronunciation_score is not null))[1]
                       - (array_agg(a.pronunciation_score order by s.completed_at asc)
                            filter (where a.pronunciation_score is not null))[1] trend
                from speaking_sessions s join speaking_assessments a on a.session_id = s.id
                where s.user_id = ? and s.status = 'COMPLETED' and s.completed_at >= ?
                """,
                rs -> rs.next()
                        ? new ProgressRow(rs.getInt("sessions"), decimal(rs.getBigDecimal("accuracy")),
                                decimal(rs.getBigDecimal("fluency")), decimal(rs.getBigDecimal("pronunciation")),
                                decimal(rs.getBigDecimal("trend")))
                        : null,
                userId, Timestamp.from(from));
        return new SpeakingDtos.ProgressResponse(windowDays, from, row == null ? 0 : row.sessions(),
                row == null ? null : row.accuracy(), row == null ? null : row.fluency(),
                row == null ? null : row.pronunciation(), row == null ? null : row.trend());
    }

    @Transactional
    void delete(UUID sessionId) {
        UUID userId = requireUserId();
        SessionUpload upload = jdbcTemplate.query("""
                select audio_bucket, audio_object_key, audio_content_type, status, ai_job_id,
                       submit_idempotency_key, audio_status
                from speaking_sessions where id = ? and user_id = ? for update
                """, rs -> rs.next() ? new SessionUpload(rs.getString("audio_bucket"), rs.getString("audio_object_key"),
                rs.getString("audio_content_type"), rs.getString("status"), rs.getObject("ai_job_id", UUID.class),
                rs.getString("submit_idempotency_key"), rs.getString("audio_status")) : null, sessionId, userId);
        if (upload == null) {
            throw new NotFoundException("SPEAKING_SESSION_NOT_FOUND", "Speaking session was not found");
        }
        if ("DELETED".equals(upload.audioStatus())) {
            return;
        }
        if ("PROCESSING".equals(upload.status())) {
            throw new ConflictException("SPEAKING_SESSION_PROCESSING", "Wait for speech processing before deletion");
        }
        storage.delete(upload.bucket(), upload.objectKey());
        jdbcTemplate.update("""
                update speaking_sessions
                set audio_status = 'DELETED', audio_deleted_at = now(), deleted_at = now() where id = ?
                """, sessionId);
    }

    private SessionUpload lockUpload(UUID sessionId, UUID userId) {
        SessionUpload upload = jdbcTemplate.query("""
                select audio_bucket, audio_object_key, audio_content_type, status, ai_job_id,
                       submit_idempotency_key, audio_status
                from speaking_sessions where id = ? and user_id = ? for update
                """, rs -> rs.next() ? new SessionUpload(rs.getString("audio_bucket"), rs.getString("audio_object_key"),
                rs.getString("audio_content_type"), rs.getString("status"), rs.getObject("ai_job_id", UUID.class),
                rs.getString("submit_idempotency_key"), rs.getString("audio_status")) : null, sessionId, userId);
        if (upload == null) {
            throw new NotFoundException("SPEAKING_SESSION_NOT_FOUND", "Speaking session was not found");
        }
        if ("DELETED".equals(upload.status())) {
            throw new ConflictException("SPEAKING_SESSION_DELETED", "The speaking recording has been deleted");
        }
        if ("DELETED".equals(upload.audioStatus())) {
            throw new ConflictException("SPEAKING_RECORDING_DELETED", "The speaking recording has been deleted");
        }
        if (!"AWAITING_UPLOAD".equals(upload.status()) && upload.jobId() == null) {
            throw new ConflictException("SPEAKING_SESSION_ALREADY_SUBMITTED",
                    "Speaking session has already been submitted");
        }
        return upload;
    }

    private List<SpeakingDtos.WordScore> wordScores(UUID sessionId) {
        List<WordScoreRow> words = jdbcTemplate.query("""
                select position, word, accuracy_score, error_type, offset_ms, duration_ms
                from speaking_word_scores where session_id = ? order by position
                """,
                (rs, row) -> new WordScoreRow(rs.getInt("position"), rs.getString("word"),
                        decimal(rs.getBigDecimal("accuracy_score")), rs.getString("error_type"),
                        rs.getObject("offset_ms", Integer.class), rs.getObject("duration_ms", Integer.class)),
                sessionId);
        return words.stream().map(word -> new SpeakingDtos.WordScore(word.position(), word.word(), word.accuracy(),
                word.errorType(), word.offsetMs(), word.durationMs(), phonemeScores(sessionId, word.position())))
                .toList();
    }

    private List<SpeakingDtos.PhonemeScore> phonemeScores(UUID sessionId, int wordPosition) {
        return jdbcTemplate.query("""
                select position, phoneme, accuracy_score from speaking_phoneme_scores
                where session_id = ? and word_position = ? order by position
                """, (rs, row) -> new SpeakingDtos.PhonemeScore(rs.getInt("position"), rs.getString("phoneme"),
                decimal(rs.getBigDecimal("accuracy_score"))), sessionId, wordPosition);
    }

    private List<SpeakingDtos.Recommendation> recommendations(UUID sessionId) {
        return jdbcTemplate
                .query("""
                        select position, content_type, content_id, reason
                        from speaking_practice_recommendations where session_id = ? order by position
                        """,
                        (rs, row) -> new SpeakingDtos.Recommendation(rs.getInt("position"),
                                rs.getString("content_type"), rs.getString("content_id"), rs.getString("reason")),
                        sessionId);
    }

    private int nextTurn(UUID userId, UUID requestedPracticeId) {
        if (requestedPracticeId == null) {
            return 1;
        }
        jdbcTemplate.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))::text", String.class,
                userId + ":speaking:" + requestedPracticeId);
        Integer lastTurn = jdbcTemplate.query("""
                select turn_number from speaking_sessions
                where user_id = ? and practice_id = ?
                order by turn_number desc limit 1 for update
                """, rs -> rs.next() ? rs.getInt("turn_number") : null, userId, requestedPracticeId);
        if (lastTurn == null) {
            throw new NotFoundException("SPEAKING_PRACTICE_NOT_FOUND", "Speaking practice was not found");
        }
        if (lastTurn >= 50) {
            throw new ConflictException("SPEAKING_PRACTICE_TURN_LIMIT", "Speaking practice cannot exceed 50 turns");
        }
        return lastTurn + 1;
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private UUID requireUserId() {
        return userDirectory.requireCurrentUserId();
    }

    record SessionUpload(String bucket, String objectKey, String contentType, String status, UUID jobId,
            String submitIdempotencyKey, String audioStatus) {
    }

    private record SessionResultRow(UUID id, String mode, String status, String recognizedText, Double accuracy,
            Double fluency, Double completeness, Double prosody, Double pronunciation, String grammarFeedback,
            String vocabularyFeedback, String provider, Instant retentionUntil, Instant audioDeletedAt) {
    }

    private record ProgressRow(int sessions, Double accuracy, Double fluency, Double pronunciation, Double trend) {
    }

    private record WordScoreRow(int position, String word, Double accuracy, String errorType, Integer offsetMs,
            Integer durationMs) {
    }
}
