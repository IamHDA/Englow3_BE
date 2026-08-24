package com.englow3.ai.tutor;

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
class TutorReplyJobHandler implements AiJobHandler {

    private final AiGateway gateway;
    private final TutorMessageRepository messageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    TutorReplyJobHandler(AiGateway gateway, TutorMessageRepository messageRepository, JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return "TUTOR_REPLY";
    }

    @Override
    public AiJobExecutionResult execute(AiJob job) {
        JsonNode payload = job.getInputPayload();
        UUID assistantMessageId = UUID.fromString(payload.path("assistantMessageId").asText());
        AiTextResult result = gateway.generate(job.getRequesterUserId(), AiCapability.TUTOR,
                payload.path("systemPrompt").asText(), payload.path("userPrompt").asText(), true);
        JsonNode structured = parse(result.content());
        String answer = structured.path("answer").asText().strip();
        if (answer.isBlank()) {
            throw new AiProviderException("AI_TUTOR_SCHEMA_INVALID", "Tutor output is missing an answer", true);
        }

        TutorMessage message = messageRepository.findById(assistantMessageId)
                .orElseThrow(() -> new AiProviderException("AI_TUTOR_MESSAGE_MISSING",
                        "The target tutor message no longer exists", false));
        message.complete(answer, result.model(), result.inputTokens(), result.outputTokens());
        messageRepository.save(message);
        saveCitations(message.getId(), payload.path("citations"));

        ObjectNode output = objectMapper.createObjectNode();
        output.put("messageId", message.getId().toString());
        output.put("answer", answer);
        output.put("language", structured.path("language").asText("en"));
        output.set("citations", payload.path("citations"));
        return new AiJobExecutionResult(output, result.inputTokens(), result.outputTokens(), result.estimatedCost());
    }

    @Override
    public void onFailure(AiJob job, boolean willRetry) {
        if (!willRetry) {
            jdbcTemplate.update("update ai_tutor_messages set status = 'FAILED' where id = ? and status = 'PENDING'",
                    job.getTargetId());
        }
    }

    private JsonNode parse(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("AI_TUTOR_SCHEMA_INVALID", "Tutor output is not valid JSON", true, ex);
        }
    }

    private void saveCitations(UUID messageId, JsonNode citations) {
        int position = 0;
        for (JsonNode citation : citations) {
            jdbcTemplate.update("""
                    insert into ai_tutor_message_citations
                        (message_id, position, content_type, content_id, label)
                    values (?, ?, ?, ?, ?)
                    on conflict (message_id, position) do nothing
                    """, messageId, position++, citation.path("contentType").asText(),
                    citation.path("contentId").asText(), citation.path("label").asText());
        }
    }
}
