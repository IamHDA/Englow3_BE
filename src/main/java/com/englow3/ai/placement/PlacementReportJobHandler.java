package com.englow3.ai.placement;

import java.sql.Timestamp;
import java.time.Instant;
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
class PlacementReportJobHandler implements AiJobHandler {

    private final AiGateway gateway;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    PlacementReportJobHandler(AiGateway gateway, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return "PLACEMENT_REPORT";
    }

    @Override
    public AiJobExecutionResult execute(AiJob job) {
        JsonNode payload = job.getInputPayload();
        UUID reportId = UUID.fromString(payload.path("reportId").asText());
        AiTextResult result = gateway.generate(job.getRequesterUserId(), AiCapability.PLACEMENT,
                payload.path("systemPrompt").asText(), payload.path("userPrompt").asText(), true);
        JsonNode structured = parse(result.content());
        String summary = structured.path("summary").asText().strip();
        if (summary.isBlank() || !structured.path("strengths").isArray()
                || !structured.path("learningGaps").isArray()) {
            throw new AiProviderException("AI_PLACEMENT_SCHEMA_INVALID", "Placement report has an invalid schema",
                    true);
        }
        jdbcTemplate.update("""
                update placement_ai_reports
                set summary = ?, strengths = ?::jsonb, learning_gaps = ?::jsonb,
                    model_name = ?, completed_at = ?
                where id = ?
                """, summary, structured.path("strengths").toString(), structured.path("learningGaps").toString(),
                result.model(), Timestamp.from(Instant.now()), reportId);
        ObjectNode output = objectMapper.createObjectNode().put("reportId", reportId.toString()).put("summary",
                summary);
        output.set("strengths", structured.path("strengths"));
        output.set("learningGaps", structured.path("learningGaps"));
        return new AiJobExecutionResult(output, result.inputTokens(), result.outputTokens());
    }

    private JsonNode parse(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("AI_PLACEMENT_SCHEMA_INVALID", "Placement report is not valid JSON", true,
                    ex);
        }
    }
}
