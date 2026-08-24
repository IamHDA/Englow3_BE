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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
class ContentDraftJobHandler implements AiJobHandler {

    private final AiGateway gateway;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    ContentDraftJobHandler(AiGateway gateway, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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
            throw new AiProviderException("AI_CONTENT_RESPONSE_INVALID", "AI content response was not valid JSON",
                    true, ex);
        }
        if (!generated.isObject() || !generated.path("items").isArray() || generated.path("items").isEmpty()) {
            throw new AiProviderException("AI_CONTENT_SCHEMA_INVALID", "AI content response failed validation", true);
        }
        jdbcTemplate.update("""
                update ai_content_drafts set generated_content = ?::jsonb, status = 'DRAFT'
                where id = ? and status in ('GENERATING', 'FAILED')
                """, json(generated), draftId);
        ObjectNode output = objectMapper.createObjectNode().put("draftId", draftId.toString())
                .put("status", "DRAFT");
        return new AiJobExecutionResult(output, result.inputTokens(), result.outputTokens());
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
