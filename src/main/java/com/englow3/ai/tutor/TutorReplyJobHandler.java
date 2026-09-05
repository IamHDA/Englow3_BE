package com.englow3.ai.tutor;

import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        TutorMode mode = parseMode(payload.path("mode").asText());
        validateModeOutput(mode, structured);
        String safetyCategory = structured.path("safetyCategory").asText();
        if (!Set.of("SAFE", "UNSUPPORTED_CLAIM", "PROVIDER_FLAGGED").contains(safetyCategory)) {
            throw new AiProviderException("AI_TUTOR_SCHEMA_INVALID", "Tutor output has an invalid safety category",
                    true);
        }
        Map<String, JsonNode> allowedReferences = references(payload.path("references"));
        List<JsonNode> selected = selectedReferences(structured.path("citationIds"), allowedReferences);
        boolean groundingRequired = payload.path("groundingRequired").asBoolean(false);
        if (groundingRequired && safetyCategory.equals("SAFE") && selected.isEmpty()) {
            throw new AiProviderException("AI_TUTOR_CITATION_REQUIRED",
                    "A grounded tutor answer must cite approved context", true);
        }
        for (JsonNode reference : selected) {
            verifyCurrentReference(reference);
        }

        TutorMessage message = messageRepository.findById(assistantMessageId)
                .orElseThrow(() -> new AiProviderException("AI_TUTOR_MESSAGE_MISSING",
                        "The target tutor message no longer exists", false));
        message.complete(answer, result.model(), result.inputTokens(), result.outputTokens());
        message.classifySafety(safetyCategory);
        messageRepository.save(message);
        saveCitations(message.getId(), selected);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("messageId", message.getId().toString());
        output.put("answer", answer);
        output.put("language", structured.path("language").asText("en"));
        output.set("citationIds", structured.path("citationIds"));
        output.put("safetyCategory", safetyCategory);
        if (structured.hasNonNull("correctedText")) {
            output.put("correctedText", structured.path("correctedText").asText());
        }
        if (structured.has("feedbackItems")) {
            output.set("feedbackItems", structured.path("feedbackItems"));
        }
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

    private TutorMode parseMode(String value) {
        try {
            return TutorMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("AI_TUTOR_MODE_INVALID", "Tutor job has an invalid mode", false, exception);
        }
    }

    static void validateModeOutput(TutorMode mode, JsonNode structured) {
        if (mode == TutorMode.SENTENCE_CORRECTION && structured.path("correctedText").asText().isBlank()) {
            throw new AiProviderException("AI_TUTOR_SCHEMA_INVALID",
                    "Sentence correction output is missing correctedText", true);
        }
        if (mode == TutorMode.WRITING_FEEDBACK
                && (!structured.path("feedbackItems").isArray() || structured.path("feedbackItems").isEmpty())) {
            throw new AiProviderException("AI_TUTOR_SCHEMA_INVALID", "Writing feedback output is missing feedbackItems",
                    true);
        }
    }

    static Map<String, JsonNode> references(JsonNode references) {
        Map<String, JsonNode> result = new HashMap<>();
        if (!references.isArray()) {
            return result;
        }
        for (JsonNode reference : references) {
            String id = reference.path("referenceId").asText();
            if (id.isBlank() || result.put(id, reference) != null) {
                throw new AiProviderException("AI_TUTOR_REFERENCE_INVALID", "Tutor job has invalid references", false);
            }
        }
        return result;
    }

    static List<JsonNode> selectedReferences(JsonNode citationIds, Map<String, JsonNode> allowed) {
        if (!citationIds.isArray()) {
            throw new AiProviderException("AI_TUTOR_SCHEMA_INVALID", "Tutor output is missing citationIds", true);
        }
        List<JsonNode> selected = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode citationId : citationIds) {
            String id = citationId.asText();
            JsonNode reference = allowed.get(id);
            if (!unique.add(id) || reference == null) {
                throw new AiProviderException("AI_TUTOR_CITATION_INVALID",
                        "Tutor output cited content outside the approved retrieval set", true);
            }
            selected.add(reference);
        }
        return selected;
    }

    private void verifyCurrentReference(JsonNode reference) {
        String type = reference.path("contentType").asText();
        String id = reference.path("contentId").asText();
        CurrentReference current = switch (type) {
            case "FLASHCARD" -> currentReference(type, id, "FLASHCARD", "flashcards", "id", "embedding_text");
            case "GRAMMAR_POINT" ->
                    currentReference(type, id, "GRAMMAR_POINT", "grammar_points", "id", "embedding_text");
            case "EXAM_ITEM" -> currentReference(type, id, "EXAM_ITEM", "exam_items", "item_id", "embedding_text");
            default -> null;
        };
        if (current == null || current.revision() != reference.path("revision").asInt(-1)
                || !current.hash().equals(reference.path("groundingHash").asText())) {
            throw new AiProviderException("AI_TUTOR_REFERENCE_STALE",
                    "Approved tutor context changed before the answer completed", false);
        }
    }

    private CurrentReference currentReference(String type, String id, String publicationType, String table,
            String idColumn, String bodyColumn) {
        String sql = "select " + bodyColumn + " body, review_status, "
                + "coalesce((select max(revision) from ai_content_publications where entity_type = ? and entity_id = ?), 0) revision "
                + "from " + table + " where " + idColumn + " = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next() || !Set.of("approved", "published", "human_verified", "human_approved")
                    .contains(rs.getString("review_status"))) {
                return null;
            }
            return new CurrentReference(rs.getInt("revision"), TutorGroundingService.sha256(rs.getString("body")));
        }, publicationType, id, id);
    }

    private void saveCitations(UUID messageId, List<JsonNode> citations) {
        int position = 0;
        for (JsonNode citation : citations) {
            jdbcTemplate.update("""
                    insert into ai_tutor_message_citations
                        (message_id, position, content_type, content_id, label, content_revision, grounding_hash)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (message_id, position) do nothing
                    """, messageId, position++, citation.path("contentType").asText(),
                    citation.path("contentId").asText(), citation.path("label").asText(),
                    citation.path("revision").asInt(), citation.path("groundingHash").asText());
        }
    }

    private record CurrentReference(int revision, String hash) {
    }
}
