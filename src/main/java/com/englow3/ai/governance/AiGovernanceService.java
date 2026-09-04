package com.englow3.ai.governance;

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
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class AiGovernanceService {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;
    private final AiContentValidator contentValidator;
    private final AiContentPublisher contentPublisher;

    AiGovernanceService(JdbcTemplate jdbcTemplate, CurrentUser currentUser, UserRepository userRepository,
            AiPromptService promptService, AiJobService jobService, ObjectMapper objectMapper,
            AiContentValidator contentValidator, AiContentPublisher contentPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.contentValidator = contentValidator;
        this.contentPublisher = contentPublisher;
    }

    @Transactional
    AiGovernanceDtos.DraftResponse generate(AiGovernanceDtos.GenerateDraftRequest request) {
        User actor = requireUser();
        String level = request.level().strip().toUpperCase();
        if (!List.of("A1", "A2", "B1", "B2", "C1").contains(level)) {
            throw new BadRequestException("AI_CONTENT_LEVEL_INVALID", "Content requires a supported CEFR level");
        }
        AiGovernanceDtos.DraftResponse existing = findDraftByIdempotency(actor.getId(), request.idempotencyKey());
        if (existing != null) {
            if (!sameGenerationRequest(existing, request, level)) {
                throw new ConflictException("AI_CONTENT_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used for a different generation request");
            }
            return existing;
        }
        RenderedPrompt prompt = promptService.render("CONTENT_DRAFT_GENERATION",
                Map.of("contentType", request.contentType().name(), "title", request.title(), "level", request.level(),
                        "itemCount", request.itemCount(), "instructions", request.instructions()));
        UUID draftId = UUID.randomUUID();
        ObjectNode generationRequest = objectMapper.createObjectNode().put("itemCount", request.itemCount())
                .put("instructions", request.instructions());
        jdbcTemplate.update("""
                insert into ai_content_drafts
                    (id, created_by, content_type, title, level, generation_request, status, prompt_version,
                     idempotency_key)
                values (?, ?, ?, ?, ?, ?::jsonb, 'GENERATING', ?, ?)
                """, draftId, actor.getId(), request.contentType().name(), request.title().strip(), level,
                json(generationRequest), prompt.version(), request.idempotencyKey());
        ObjectNode payload = objectMapper.createObjectNode().put("draftId", draftId.toString())
                .put("contentType", request.contentType().name()).put("level", level)
                .put("systemPrompt", prompt.systemPrompt()).put("userPrompt", prompt.userPrompt());
        AiJob job = jobService.submitForCurrentUser(AiCapability.CONTENT_GENERATION, "CONTENT_DRAFT_GENERATION",
                "AI_CONTENT_DRAFT", draftId, prompt.version(), payload, "CONTENT:" + request.idempotencyKey());
        jdbcTemplate.update("update ai_content_drafts set ai_job_id = ? where id = ?", job.getId(), draftId);
        audit(actor.getId(), "CONTENT_DRAFT_GENERATE", "AI_CONTENT_DRAFT", draftId.toString(), generationRequest);
        return draft(draftId);
    }

    @Transactional(readOnly = true)
    List<AiGovernanceDtos.DraftResponse> drafts(String status) {
        if (status != null && !List
                .of("GENERATING", "DRAFT", "PENDING_REVIEW", "APPROVED", "PUBLISHED", "REJECTED", "ARCHIVED", "FAILED")
                .contains(status)) {
            throw new BadRequestException("AI_CONTENT_STATUS_INVALID", "Unknown AI content status");
        }
        String sql = """
                select id, content_type, title, level, status, ai_job_id, generated_content, validation_report,
                       revision, published_entities, review_reason, created_at, updated_at
                from ai_content_drafts
                """ + (status == null ? "" : " where status = ?") + " order by created_at desc limit 200";
        return status == null ? jdbcTemplate.query(sql, this::mapDraft)
                : jdbcTemplate.query(sql, this::mapDraft, status);
    }

    @Transactional
    AiGovernanceDtos.DraftResponse submitForReview(UUID draftId) {
        User actor = requireUser();
        int updated = jdbcTemplate.update("""
                update ai_content_drafts set status = 'PENDING_REVIEW', review_reason = null
                where id = ? and created_by = ? and status in ('DRAFT', 'REJECTED')
                  and validation_report->>'valid' = 'true' and content_hash is not null
                """, draftId, actor.getId());
        if (updated == 0) {
            throw new ConflictException("AI_CONTENT_NOT_SUBMITTABLE",
                    "Only your generated draft or rejected content can be submitted");
        }
        audit(actor.getId(), "CONTENT_SUBMIT_REVIEW", "AI_CONTENT_DRAFT", draftId.toString(), null);
        return draft(draftId);
    }

    @Transactional
    AiGovernanceDtos.DraftResponse updateDraft(UUID draftId, AiGovernanceDtos.UpdateDraftRequest request) {
        User actor = requireUser();
        DraftMetadata metadata = editableMetadata(draftId, actor.getId());
        AiContentValidator.ValidationResult validation = contentValidator.validate(
                AiGovernanceDtos.ContentType.valueOf(metadata.contentType()), metadata.level(),
                request.generatedContent());
        validateConcepts(validation);
        int nextRevision = metadata.revision() + 1;
        int updated = jdbcTemplate.update("""
                update ai_content_drafts
                set title = ?, generated_content = ?::jsonb, validation_report = ?::jsonb,
                    content_hash = ?, revision = ?, review_reason = null, status = 'DRAFT'
                where id = ? and created_by = ? and status in ('DRAFT', 'REJECTED')
                """, request.title().strip(), json(request.generatedContent()), json(validation.report()),
                validation.contentHash(), nextRevision, draftId, actor.getId());
        if (updated == 0) {
            throw new ConflictException("AI_CONTENT_NOT_EDITABLE", "Only your draft or rejected content can be edited");
        }
        jdbcTemplate.update("""
                insert into ai_content_draft_revisions
                    (draft_id, revision, title, generated_content, validation_report, content_hash, created_by)
                values (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """, draftId, nextRevision, request.title().strip(), json(request.generatedContent()),
                json(validation.report()), validation.contentHash(), actor.getId());
        audit(actor.getId(), "CONTENT_DRAFT_EDIT", "AI_CONTENT_DRAFT", draftId.toString(), null);
        return draft(draftId);
    }

    @Transactional
    AiGovernanceDtos.DraftResponse review(UUID draftId, boolean publish, String reason) {
        User reviewer = requireUser();
        int updated = jdbcTemplate.update("""
                update ai_content_drafts
                set status = ?, reviewed_by = ?, review_reason = ?, reviewed_at = now(),
                    published_at = null
                where id = ? and status = 'PENDING_REVIEW' and generated_content is not null
                  and validation_report->>'valid' = 'true' and created_by <> ?
                """, publish ? "APPROVED" : "REJECTED", reviewer.getId(), reason.strip(), draftId, reviewer.getId());
        if (updated == 0) {
            throw new ConflictException("AI_CONTENT_NOT_REVIEWABLE", "Content is not pending review");
        }
        ObjectNode details = objectMapper.createObjectNode().put("decision", publish ? "APPROVED" : "REJECTED")
                .put("reason", reason);
        audit(reviewer.getId(), "CONTENT_REVIEW", "AI_CONTENT_DRAFT", draftId.toString(), details);
        return draft(draftId);
    }

    @Transactional
    AiGovernanceDtos.DraftResponse publish(UUID draftId) {
        User publisher = requireUser();
        PublicationDraft draft = publicationDraft(draftId);
        AiGovernanceDtos.ContentType type = AiGovernanceDtos.ContentType.valueOf(draft.contentType());
        AiContentValidator.ValidationResult validation = contentValidator.validate(type, draft.level(),
                draft.content());
        if (!validation.contentHash().equals(draft.contentHash())) {
            throw new ConflictException("AI_CONTENT_CHANGED_AFTER_APPROVAL",
                    "Approved content no longer matches its validated revision");
        }
        validateConcepts(validation);
        JsonNode entities = contentPublisher.publish(type, draft.level(), draft.content());
        for (JsonNode entity : entities) {
            jdbcTemplate.update("""
                    insert into ai_content_publications
                        (draft_id, revision, entity_type, entity_id, published_by)
                    values (?, ?, ?, ?, ?)
                    """, draftId, draft.revision(), entity.path("entityType").asText(),
                    entity.path("entityId").asText(), publisher.getId());
        }
        int updated = jdbcTemplate.update("""
                update ai_content_drafts
                set status = 'PUBLISHED', published_entities = ?::jsonb, published_at = now()
                where id = ? and status = 'APPROVED' and revision = ? and content_hash = ?
                """, json(entities), draftId, draft.revision(), draft.contentHash());
        if (updated == 0) {
            throw new ConflictException("AI_CONTENT_NOT_PUBLISHABLE", "Content is no longer approved for publication");
        }
        audit(publisher.getId(), "CONTENT_PUBLISH", "AI_CONTENT_DRAFT", draftId.toString(), entities);
        return draft(draftId);
    }

    @Transactional
    AiGovernanceDtos.DraftResponse archive(UUID draftId, String reason) {
        User actor = requireUser();
        JsonNode entities = jdbcTemplate.query("""
                select published_entities from ai_content_drafts
                where id = ? and status = 'PUBLISHED' for update
                """, rs -> rs.next() ? parse(rs.getString("published_entities")) : null, draftId);
        if (entities == null || !entities.isArray() || entities.isEmpty()) {
            throw new ConflictException("AI_CONTENT_NOT_ARCHIVABLE", "Published content entities were not found");
        }
        contentPublisher.archive(entities);
        jdbcTemplate.update("""
                update ai_content_drafts set status = 'ARCHIVED', review_reason = ? where id = ?
                """, reason.strip(), draftId);
        audit(actor.getId(), "CONTENT_ARCHIVE", "AI_CONTENT_DRAFT", draftId.toString(),
                objectMapper.createObjectNode().put("reason", reason));
        return draft(draftId);
    }

    @Transactional
    AiGovernanceDtos.FeedbackResponse report(AiGovernanceDtos.FeedbackRequest request) {
        User reporter = requireUser();
        if (request.aiJobId() != null) {
            Integer owned = jdbcTemplate.queryForObject(
                    "select count(*) from ai_jobs where id = ? and requester_user_id = ?", Integer.class,
                    request.aiJobId(), reporter.getId());
            if (owned == null || owned == 0) {
                throw new NotFoundException("AI_JOB_NOT_FOUND", "AI job was not found");
            }
        }
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into ai_feedback_reports
                    (id, reporter_user_id, ai_job_id, capability, category, details)
                values (?, ?, ?, ?, ?, ?)
                """, id, reporter.getId(), request.aiJobId(), request.capability().name(), request.category().name(),
                request.details());
        return feedback(id);
    }

    @Transactional(readOnly = true)
    List<AiGovernanceDtos.FeedbackResponse> reports(String status) {
        String effectiveStatus = status == null ? "OPEN" : status;
        try {
            AiGovernanceDtos.FeedbackStatus.valueOf(effectiveStatus);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("AI_FEEDBACK_STATUS_INVALID", "Unknown feedback status");
        }
        return jdbcTemplate.query("""
                select id, capability, category, status, details, resolution, created_at, resolved_at
                from ai_feedback_reports where status = ? order by created_at asc limit 200
                """, this::mapFeedback, effectiveStatus);
    }

    @Transactional
    AiGovernanceDtos.FeedbackResponse resolve(UUID reportId, AiGovernanceDtos.ResolveFeedbackRequest request) {
        if (request.status() == AiGovernanceDtos.FeedbackStatus.OPEN) {
            throw new BadRequestException("AI_FEEDBACK_RESOLUTION_INVALID", "Resolution status cannot be OPEN");
        }
        User actor = requireUser();
        int updated = jdbcTemplate.update("""
                update ai_feedback_reports set status = ?, resolution = ?, resolved_by = ?,
                    resolved_at = case when ? in ('RESOLVED', 'DISMISSED') then now() else null end
                where id = ?
                """, request.status().name(), request.resolution(), actor.getId(), request.status().name(), reportId);
        if (updated == 0) {
            throw new NotFoundException("AI_FEEDBACK_NOT_FOUND", "AI feedback report was not found");
        }
        audit(actor.getId(), "FEEDBACK_RESOLVE", "AI_FEEDBACK_REPORT", reportId.toString(),
                objectMapper.createObjectNode().put("status", request.status().name()));
        return feedback(reportId);
    }

    private AiGovernanceDtos.DraftResponse findDraftByIdempotency(UUID userId, String key) {
        return jdbcTemplate.query("""
                select id, content_type, title, level, status, ai_job_id, generated_content, validation_report,
                       revision, published_entities, review_reason, created_at, updated_at
                from ai_content_drafts where created_by = ? and idempotency_key = ?
                """, rs -> rs.next() ? mapDraft(rs, 0) : null, userId, key);
    }

    private boolean sameGenerationRequest(AiGovernanceDtos.DraftResponse existing,
            AiGovernanceDtos.GenerateDraftRequest request, String level) {
        if (!existing.contentType().equals(request.contentType().name())
                || !existing.title().equals(request.title().strip()) || !existing.level().equals(level)) {
            return false;
        }
        Integer matches = jdbcTemplate.queryForObject("""
                select count(*) from ai_content_drafts
                where id = ? and (generation_request->>'itemCount')::integer = ?
                  and generation_request->>'instructions' = ?
                """, Integer.class, existing.id(), request.itemCount(), request.instructions());
        return matches != null && matches == 1;
    }

    private AiGovernanceDtos.DraftResponse draft(UUID id) {
        AiGovernanceDtos.DraftResponse response = jdbcTemplate.query("""
                select id, content_type, title, level, status, ai_job_id, generated_content, validation_report,
                       revision, published_entities, review_reason, created_at, updated_at
                from ai_content_drafts where id = ?
                """, rs -> rs.next() ? mapDraft(rs, 0) : null, id);
        if (response == null) {
            throw new NotFoundException("AI_CONTENT_NOT_FOUND", "AI content draft was not found");
        }
        return response;
    }

    private AiGovernanceDtos.DraftResponse mapDraft(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AiGovernanceDtos.DraftResponse(rs.getObject("id", UUID.class), rs.getString("content_type"),
                rs.getString("title"), rs.getString("level"), rs.getString("status"),
                rs.getObject("ai_job_id", UUID.class), parse(rs.getString("generated_content")),
                parse(rs.getString("validation_report")), rs.getInt("revision"),
                parse(rs.getString("published_entities")), rs.getString("review_reason"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private DraftMetadata editableMetadata(UUID draftId, UUID actorId) {
        DraftMetadata metadata = jdbcTemplate.query("""
                select content_type, level, revision from ai_content_drafts
                where id = ? and created_by = ? and status in ('DRAFT', 'REJECTED') for update
                """,
                rs -> rs.next()
                        ? new DraftMetadata(rs.getString("content_type"), rs.getString("level"), rs.getInt("revision"))
                        : null,
                draftId, actorId);
        if (metadata == null) {
            throw new ConflictException("AI_CONTENT_NOT_EDITABLE", "Only your draft or rejected content can be edited");
        }
        return metadata;
    }

    private PublicationDraft publicationDraft(UUID draftId) {
        PublicationDraft draft = jdbcTemplate.query("""
                select content_type, level, generated_content, content_hash, revision
                from ai_content_drafts where id = ? and status = 'APPROVED' for update
                """,
                rs -> rs.next() ? new PublicationDraft(rs.getString("content_type"), rs.getString("level"),
                        parse(rs.getString("generated_content")), rs.getString("content_hash"), rs.getInt("revision"))
                        : null,
                draftId);
        if (draft == null) {
            throw new ConflictException("AI_CONTENT_NOT_PUBLISHABLE", "Only approved content can be published");
        }
        return draft;
    }

    private void validateConcepts(AiContentValidator.ValidationResult validation) {
        for (String conceptId : validation.conceptIds()) {
            Integer exists = jdbcTemplate.queryForObject("select count(*) from concepts where concept_id = ?",
                    Integer.class, conceptId);
            if (exists == null || exists == 0) {
                throw new BadRequestException("AI_CONTENT_CONCEPT_NOT_FOUND",
                        "Generated content references an unknown concept: " + conceptId);
            }
        }
    }

    private AiGovernanceDtos.FeedbackResponse feedback(UUID id) {
        AiGovernanceDtos.FeedbackResponse response = jdbcTemplate.query("""
                select id, capability, category, status, details, resolution, created_at, resolved_at
                from ai_feedback_reports where id = ?
                """, rs -> rs.next() ? mapFeedback(rs, 0) : null, id);
        if (response == null) {
            throw new NotFoundException("AI_FEEDBACK_NOT_FOUND", "AI feedback report was not found");
        }
        return response;
    }

    private AiGovernanceDtos.FeedbackResponse mapFeedback(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new AiGovernanceDtos.FeedbackResponse(rs.getObject("id", UUID.class), rs.getString("capability"),
                rs.getString("category"), rs.getString("status"), rs.getString("details"), rs.getString("resolution"),
                rs.getTimestamp("created_at").toInstant(), resolvedAt == null ? null : resolvedAt.toInstant());
    }

    private JsonNode parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored AI JSON is invalid", ex);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize AI governance data", ex);
        }
    }

    private void audit(UUID actor, String action, String targetType, String targetId, JsonNode details) {
        jdbcTemplate.update("""
                insert into ai_admin_audit_log (id, actor_user_id, action, target_type, target_id, details)
                values (?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), actor, action, targetType, targetId,
                json(details == null ? objectMapper.createObjectNode() : details));
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }

    private record DraftMetadata(String contentType, String level, int revision) {
    }

    private record PublicationDraft(String contentType, String level, JsonNode content, String contentHash,
            int revision) {
    }
}
