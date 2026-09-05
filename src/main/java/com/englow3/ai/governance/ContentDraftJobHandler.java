package com.englow3.ai.governance;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiGateway;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobExecutionResult;
import com.englow3.ai.foundation.AiJobHandler;
import com.englow3.ai.foundation.AiProviderException;
import com.englow3.ai.foundation.AiTextResult;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
class ContentDraftJobHandler implements AiJobHandler {

    private final AiGateway gateway;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AiContentValidator validator;

    ContentDraftJobHandler(AiGateway gateway, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            AiContentValidator validator) {
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public String jobType() {
        return "CONTENT_DRAFT_GENERATION";
    }

    @Override
    public AiJobExecutionResult execute(AiJob job) {
        JsonNode input = job.getInputPayload();
        UUID draftId = UUID.fromString(input.path("draftId").asText());
        AiTextResult result = gateway.generate(job.getRequesterUserId(), AiCapability.CONTENT_GENERATION,
                input.path("systemPrompt").asText(), input.path("userPrompt").asText(), true);
        JsonNode generated;
        try {
            generated = objectMapper.readTree(result.content());
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("AI_CONTENT_RESPONSE_INVALID", "AI content response was not valid JSON", true,
                    ex);
        }
        AiContentValidator.ValidationResult validation;
        try {
            validation = validator.validate(AiGovernanceDtos.ContentType.valueOf(input.path("contentType").asText()),
                    input.path("level").asText(), generated);
        } catch (BadRequestException | IllegalArgumentException ex) {
            throw new AiProviderException("AI_CONTENT_SCHEMA_INVALID", "AI content response failed validation", true,
                    ex);
        }
        for (String conceptId : validation.conceptIds()) {
            Integer exists = jdbcTemplate.queryForObject("select count(*) from concepts where concept_id = ?",
                    Integer.class, conceptId);
            if (exists == null || exists == 0) {
                throw new AiProviderException("AI_CONTENT_CONCEPT_INVALID",
                        "AI content referenced an unknown learning concept", true);
            }
        }
        jdbcTemplate.update("""
                update ai_content_drafts
                set generated_content = ?::jsonb, validation_report = ?::jsonb,
                    content_hash = ?, revision = 1, status = 'DRAFT'
                where id = ? and status in ('GENERATING', 'FAILED')
                """, json(generated), json(validation.report()), validation.contentHash(), draftId);
        jdbcTemplate.update("""
                insert into ai_content_draft_revisions
                    (draft_id, revision, title, generated_content, validation_report, content_hash, created_by)
                select id, 1, title, generated_content, validation_report, content_hash, created_by
                from ai_content_drafts where id = ?
                on conflict (draft_id, revision) do nothing
                """, draftId);
        ObjectNode output = objectMapper.createObjectNode().put("draftId", draftId.toString()).put("status", "DRAFT");
        return new AiJobExecutionResult(output, result.inputTokens(), result.outputTokens(), result.estimatedCost());
    }

    @Override
    public void onFailure(AiJob job, boolean willRetry) {
        if (!willRetry) {
            jdbcTemplate.update("update ai_content_drafts set status = 'FAILED' where id = ?", job.getTargetId());
        }
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize generated content", ex);
        }
    }
}
