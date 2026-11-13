package com.englow3.translation.worker;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;
import com.englow3.shared.error.DomainException;
import com.englow3.translation.helper.TranslationRubric;
import com.englow3.translation.service.TranslationGradeWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/** Grades one translation. The provider is called here, outside any transaction; the grade is written in complete. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class TranslationAssessmentHandler implements AiJobHandler {

    private static final int MAX_OUTPUT_TOKENS = 1500;

    private final LlmClient llm;
    private final TranslationGradeWriter writer;
    private final ObjectMapper mapper;

    @Override
    public String handles() {
        return "TRANSLATION_ASSESSMENT";
    }

    @Override
    public Outcome run(UUID jobId, UUID targetId, String inputPayload) {
        try {
            JsonNode input = mapper.readTree(inputPayload);
            if (!targetId.toString().equals(input.path("attemptId").asText())) {
                return Outcome.permanentFailure("TRANSLATION_PAYLOAD_INVALID", "Attempt identity does not match");
            }
            // The id stays behind: the grader needs the sentences, not this system's keys.
            var evidence = mapper.createObjectNode();
            evidence.put("sourceVi", input.path("sourceVi").asText());
            evidence.put("referenceEn", input.path("referenceEn").asText());
            evidence.put("learnerTranslation", input.path("learnerTranslation").asText());

            String raw = llm.generateStructured(TranslationRubric.systemPrompt(), mapper.writeValueAsString(evidence),
                    MAX_OUTPUT_TOKENS);
            String content = mapper.readTree(raw).path("content").asText("");
            return Outcome.succeeded(TranslationRubric.validate(mapper, content));
        } catch (LlmException e) {
            return e.isRetryable()
                    ? Outcome.transientFailure(e.getCode(), "The grading provider is unavailable")
                            .retryAfter(e.getRetryAfter())
                    : Outcome.permanentFailure(e.getCode(), "The grading provider rejected the request");
        } catch (DomainException e) {
            // An unusable report is worth one more try: the next sample of the same model is usually well formed.
            return Outcome.transientFailure(e.getCode(), e.getMessage());
        } catch (JsonProcessingException e) {
            return Outcome.permanentFailure("TRANSLATION_PAYLOAD_INVALID", "The grading request is invalid");
        }
    }

    @Override
    public void complete(UUID targetId, String inputPayload, String outputPayload) {
        writer.storeGrade(targetId, outputPayload);
    }

    @Override
    public void onGaveUp(UUID targetId, String errorCode) {
        writer.markFailed(targetId, errorCode);
    }
}
