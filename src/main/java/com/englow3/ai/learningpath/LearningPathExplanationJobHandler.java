package com.englow3.ai.learningpath;

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
class LearningPathExplanationJobHandler implements AiJobHandler {

    private final AiGateway gateway;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    LearningPathExplanationJobHandler(AiGateway gateway, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return "LEARNING_PATH_EXPLANATION";
    }

    @Override
    public AiJobExecutionResult execute(AiJob job) {
        JsonNode payload = job.getInputPayload();
        UUID pathId = UUID.fromString(payload.path("pathId").asText());
        AiTextResult result = gateway.generate(job.getRequesterUserId(), AiCapability.LEARNING_PATH,
                payload.path("systemPrompt").asText(), payload.path("userPrompt").asText(), true);
        JsonNode structured = parse(result.content());
        String explanation = structured.path("explanation").asText().strip();
        if (explanation.isBlank() || !structured.path("weeklyAdvice").isArray()) {
            throw new AiProviderException("AI_LEARNING_PATH_SCHEMA_INVALID",
                    "Learning path explanation has an invalid schema", true);
        }
        jdbcTemplate.update("update learning_paths set explanation = ? where id = ?", explanation, pathId);
        ObjectNode output = objectMapper.createObjectNode().put("pathId", pathId.toString()).put("explanation",
                explanation);
        output.set("weeklyAdvice", structured.path("weeklyAdvice"));
        return new AiJobExecutionResult(output, result.inputTokens(), result.outputTokens());
    }

    private JsonNode parse(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("AI_LEARNING_PATH_SCHEMA_INVALID",
                    "Learning path explanation is not valid JSON", true, ex);
        }
    }
}
