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
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class SpeakingSessionService {

    private static final Map<String, String> CONTENT_TYPES = Map.of("audio/wav; codecs=audio/pcm; samplerate=16000",
            "wav", "audio/ogg; codecs=opus", "ogg");

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ObjectStorageClient storage;
    private final SpeechProperties properties;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;

    SpeakingSessionService(JdbcTemplate jdbcTemplate, UserRepository userRepository, CurrentUser currentUser,
            ObjectStorageClient storage, SpeechProperties properties, AiPromptService promptService,
            AiJobService jobService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.storage = storage;
        this.properties = properties;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    SpeakingDtos.CreateSessionResponse create(SpeakingDtos.CreateSessionRequest request) {
        User user = requireUser();
        String extension = CONTENT_TYPES.get(request.contentType());
        if (extension == null) {
            throw new BadRequestException("SPEAKING_AUDIO_TYPE_UNSUPPORTED", "Use WAV PCM 16 kHz or OGG Opus audio");
        }
        String reference = request.referenceText() == null ? null : request.referenceText().strip();
        if (request.mode() == SpeakingDtos.Mode.READ_ALOUD && (reference == null || reference.isBlank())) {
            throw new BadRequestException("SPEAKING_REFERENCE_REQUIRED", "Read-aloud sessions require reference text");
        }
        UUID sessionId = UUID.randomUUID();
        String bucket = storage.defaultBucket();
        String objectKey = "users/%s/speaking/%s/input.%s".formatted(user.getId(), sessionId, extension);
        Instant now = Instant.now();
        Instant retentionUntil = now.plus(properties.retention());
        jdbcTemplate.update("""
                insert into speaking_sessions
                    (id, user_id, mode, locale, reference_text, audio_bucket, audio_object_key,
                     audio_content_type, status, consented_at, retention_until)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'AWAITING_UPLOAD', ?, ?)
                """, sessionId, user.getId(), request.mode().name(), properties.locale(), reference, bucket, objectKey,
                request.contentType(), Timestamp.from(now), Timestamp.from(retentionUntil));
        URL uploadUrl = storage.presignPut(bucket, objectKey, request.contentType(), properties.uploadUrlTtl());
        return new SpeakingDtos.CreateSessionResponse(sessionId, uploadUrl, objectKey,
                now.plus(properties.uploadUrlTtl()), request.contentType());
    }

    @Transactional
    SpeakingDtos.SubmitSessionResponse submit(UUID sessionId, String idempotencyKey) {
        User user = requireUser();
        SessionUpload upload = lockUpload(sessionId, user.getId());
        if (!"AWAITING_UPLOAD".equals(upload.status()) && upload.jobId() != null) {
            return new SpeakingDtos.SubmitSessionResponse(sessionId, upload.jobId(), "EXISTING");
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
                sessionId, prompt.version(), payload, idempotencyKey);
        jdbcTemplate.update("""
                update speaking_sessions
                set status = 'PROCESSING', ai_job_id = ?, audio_size_bytes = ?, submitted_at = now()
                where id = ?
                """, job.getId(), metadata.contentLength(), sessionId);
        return new SpeakingDtos.SubmitSessionResponse(sessionId, job.getId(), job.getStatus().name());
    }

    @Transactional(readOnly = true)
    SpeakingDtos.SessionResult result(UUID sessionId) {
        User user = requireUser();
        SessionResultRow row = jdbcTemplate.query("""
                select s.id, s.mode, s.status, s.retention_until, a.recognized_text, a.accuracy_score,
                       a.fluency_score, a.completeness_score, a.prosody_score, a.pronunciation_score,
                       a.grammar_feedback, a.vocabulary_feedback
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
                    rs.getTimestamp("retention_until").toInstant());
        }, sessionId, user.getId());
        if (row == null) {
            throw new NotFoundException("SPEAKING_SESSION_NOT_FOUND", "Speaking session was not found");
        }
        return new SpeakingDtos.SessionResult(row.id(), row.mode(), row.status(), row.recognizedText(), row.accuracy(),
                row.fluency(), row.completeness(), row.prosody(), row.pronunciation(), row.grammarFeedback(),
                row.vocabularyFeedback(), wordScores(sessionId), row.retentionUntil());
    }

    @Transactional(readOnly = true)
    List<SpeakingDtos.SessionSummary> history() {
        User user = requireUser();
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
                user.getId());
    }

    @Transactional
    void delete(UUID sessionId) {
        User user = requireUser();
        SessionUpload upload = jdbcTemplate.query("""
                select audio_bucket, audio_object_key, audio_content_type, status, ai_job_id
                from speaking_sessions where id = ? and user_id = ? for update
                """, rs -> rs.next() ? new SessionUpload(rs.getString("audio_bucket"), rs.getString("audio_object_key"),
                rs.getString("audio_content_type"), rs.getString("status"), rs.getObject("ai_job_id", UUID.class))
                : null, sessionId, user.getId());
        if (upload == null) {
            throw new NotFoundException("SPEAKING_SESSION_NOT_FOUND", "Speaking session was not found");
        }
        if ("PROCESSING".equals(upload.status())) {
            throw new ConflictException("SPEAKING_SESSION_PROCESSING", "Wait for speech processing before deletion");
        }
        storage.delete(upload.bucket(), upload.objectKey());
        jdbcTemplate.update("""
                update speaking_sessions set status = 'DELETED', deleted_at = now() where id = ?
                """, sessionId);
    }

    private SessionUpload lockUpload(UUID sessionId, UUID userId) {
        SessionUpload upload = jdbcTemplate.query("""
                select audio_bucket, audio_object_key, audio_content_type, status, ai_job_id
                from speaking_sessions where id = ? and user_id = ? for update
                """, rs -> rs.next() ? new SessionUpload(rs.getString("audio_bucket"), rs.getString("audio_object_key"),
                rs.getString("audio_content_type"), rs.getString("status"), rs.getObject("ai_job_id", UUID.class))
                : null, sessionId, userId);
        if (upload == null) {
            throw new NotFoundException("SPEAKING_SESSION_NOT_FOUND", "Speaking session was not found");
        }
        if ("DELETED".equals(upload.status())) {
            throw new ConflictException("SPEAKING_SESSION_DELETED", "The speaking recording has been deleted");
        }
        if (!"AWAITING_UPLOAD".equals(upload.status()) && upload.jobId() == null) {
            throw new ConflictException("SPEAKING_SESSION_ALREADY_SUBMITTED",
                    "Speaking session has already been submitted");
        }
        return upload;
    }

    private List<SpeakingDtos.WordScore> wordScores(UUID sessionId) {
        return jdbcTemplate.query("""
                select position, word, accuracy_score, error_type, offset_ms, duration_ms
                from speaking_word_scores where session_id = ? order by position
                """,
                (rs, row) -> new SpeakingDtos.WordScore(rs.getInt("position"), rs.getString("word"),
                        decimal(rs.getBigDecimal("accuracy_score")), rs.getString("error_type"),
                        rs.getObject("offset_ms", Integer.class), rs.getObject("duration_ms", Integer.class)),
                sessionId);
    }

    private static Double decimal(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }

    record SessionUpload(String bucket, String objectKey, String contentType, String status, UUID jobId) {
    }

    private record SessionResultRow(UUID id, String mode, String status, String recognizedText, Double accuracy,
            Double fluency, Double completeness, Double prosody, Double pronunciation, String grammarFeedback,
            String vocabularyFeedback, Instant retentionUntil) {
    }
}
