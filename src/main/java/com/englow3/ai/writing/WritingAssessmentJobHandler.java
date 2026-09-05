package com.englow3.ai.writing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
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
class WritingAssessmentJobHandler implements AiJobHandler {

    private static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1");

    private final AiGateway gateway;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    WritingAssessmentJobHandler(AiGateway gateway, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String jobType() {
        return "WRITING_ASSESSMENT";
    }

    @Override
    public AiJobExecutionResult execute(AiJob job) {
        JsonNode payload = job.getInputPayload();
        UUID submissionId = UUID.fromString(payload.path("submissionId").asText());
        AiTextResult result = gateway.generate(job.getRequesterUserId(), AiCapability.WRITING,
                payload.path("systemPrompt").asText(), payload.path("userPrompt").asText(), true);
        JsonNode raw = parse(result.content());
        ValidatedAssessment assessment = validate(raw, payload.path("criteria"),
                payload.path("learnerResponse").asText());

        jdbcTemplate.update("delete from writing_assessments where submission_id = ?", submissionId);
        jdbcTemplate.update("""
                insert into writing_assessments
                    (submission_id, overall_score, cefr_level, summary, criterion_scores, strengths,
                     improvements, corrected_response, sample_revision, provider_name, model_name, raw_result)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?::jsonb)
                """, submissionId, assessment.overallScore(), assessment.cefrLevel(), assessment.summary(),
                assessment.criterionScores().toString(), assessment.strengths().toString(),
                assessment.improvements().toString(), assessment.correctedResponse(), assessment.sampleRevision(),
                job.getProviderName(), result.model(), raw.toString());
        jdbcTemplate.update("""
                update writing_submissions set status = 'COMPLETED', completed_at = now() where id = ?
                """, submissionId);

        ObjectNode output = objectMapper.createObjectNode().put("submissionId", submissionId.toString())
                .put("overallScore", assessment.overallScore()).put("cefrLevel", assessment.cefrLevel());
        return new AiJobExecutionResult(output, result.inputTokens(), result.outputTokens(), result.estimatedCost());
    }

    @Override
    public void onFailure(AiJob job, boolean willRetry) {
        if (!willRetry) {
            jdbcTemplate.update("update writing_submissions set status = 'FAILED' where id = ?", job.getTargetId());
        }
    }

    static ValidatedAssessment validate(JsonNode raw, JsonNode rubricCriteria, String learnerResponse) {
        if (!raw.isObject() || !raw.path("criterionScores").isArray()) {
            throw invalid("Writing assessment is not a JSON object with criterion scores");
        }
        Set<String> expected = new HashSet<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (JsonNode criterion : rubricCriteria) {
            String name = requiredText(criterion, "name");
            if (!expected.add(name)) {
                throw invalid("Writing rubric contains duplicate criteria");
            }
            BigDecimal weight = decimal(criterion.path("weight"));
            if (weight.signum() <= 0) {
                throw invalid("Writing rubric contains an invalid weight");
            }
            totalWeight = totalWeight.add(weight);
        }
        if (expected.isEmpty() || totalWeight.signum() <= 0) {
            throw invalid("Writing rubric is empty");
        }

        Set<String> received = new HashSet<>();
        BigDecimal weightedScore = BigDecimal.ZERO;
        for (JsonNode score : raw.path("criterionScores")) {
            String name = requiredText(score, "criterion");
            if (!expected.contains(name) || !received.add(name)) {
                throw invalid("Writing assessment contains unknown or duplicate criteria");
            }
            BigDecimal value = decimal(score.path("score"));
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw invalid("Writing criterion score is outside 0 to 100");
            }
            requiredText(score, "feedback");
            JsonNode evidence = score.path("evidence");
            if (!evidence.isArray() || evidence.isEmpty()) {
                throw invalid("Every writing criterion requires evidence");
            }
            for (JsonNode quote : evidence) {
                String text = quote.isTextual() ? quote.asText().strip() : "";
                if (text.isEmpty() || !learnerResponse.contains(text)) {
                    throw invalid("Writing evidence must be an exact quote from the learner response");
                }
            }
            BigDecimal weight = weight(rubricCriteria, name);
            weightedScore = weightedScore.add(value.multiply(weight));
        }
        if (!received.equals(expected)) {
            throw invalid("Writing assessment does not cover every rubric criterion");
        }

        String cefr = requiredText(raw, "cefrLevel");
        if (!CEFR_LEVELS.contains(cefr)) {
            throw invalid("Writing assessment contains an unsupported CEFR level");
        }
        JsonNode strengths = nonEmptyTextArray(raw, "strengths");
        JsonNode improvements = nonEmptyTextArray(raw, "improvements");
        BigDecimal overall = weightedScore.divide(totalWeight, 2, RoundingMode.HALF_UP);
        return new ValidatedAssessment(overall, cefr, requiredText(raw, "summary"), raw.path("criterionScores"),
                strengths, improvements, requiredText(raw, "correctedResponse"), requiredText(raw, "sampleRevision"));
    }

    private JsonNode parse(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException("AI_WRITING_SCHEMA_INVALID", "Writing assessment is not valid JSON", true,
                    ex);
        }
    }

    private static BigDecimal weight(JsonNode criteria, String name) {
        for (JsonNode criterion : criteria) {
            if (name.equals(criterion.path("name").asText())) {
                return decimal(criterion.path("weight"));
            }
        }
        throw invalid("Writing assessment criterion has no rubric weight");
    }

    private static JsonNode nonEmptyTextArray(JsonNode source, String field) {
        JsonNode value = source.path(field);
        if (!value.isArray() || value.isEmpty()) {
            throw invalid("Writing assessment field " + field + " must be a non-empty array");
        }
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid("Writing assessment field " + field + " contains an empty item");
            }
        }
        return value;
    }

    private static String requiredText(JsonNode source, String field) {
        JsonNode value = source.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid("Writing assessment field " + field + " is required");
        }
        return value.asText().strip();
    }

    private static BigDecimal decimal(JsonNode value) {
        if (!value.isNumber()) {
            throw invalid("Writing assessment score or weight is not numeric");
        }
        return value.decimalValue();
    }

    private static AiProviderException invalid(String message) {
        return new AiProviderException("AI_WRITING_SCHEMA_INVALID", message, true);
    }

    record ValidatedAssessment(BigDecimal overallScore, String cefrLevel, String summary, JsonNode criterionScores,
            JsonNode strengths, JsonNode improvements, String correctedResponse, String sampleRevision) {
    }
}
