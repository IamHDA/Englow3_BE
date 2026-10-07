package com.englow3.assessment.worker;

import java.util.UUID;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.*;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.helper.AssessmentRubric;
import com.englow3.assessment.service.AssessmentResultWriter;
import com.englow3.shared.error.DomainException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class ProductiveAssessmentHandler implements AiJobHandler {
    private final LlmClient llm;
    private final SpeechAssessmentClient speech;
    private final ObjectStorageClient storage;
    private final AssessmentResultWriter writer;
    private final ObjectMapper mapper;
    @Value("${app.storage.speaking-bucket:speaking}")
    private String bucket;

    public String handles() {
        return "PRODUCTIVE_ASSESSMENT";
    }

    public Outcome run(UUID jobId, UUID targetId, String inputPayload) {
        try {
            JsonNode input = mapper.readTree(inputPayload);
            if (!targetId.toString().equals(input.path("attemptId").asText()))
                return Outcome.permanentFailure("ASSESSMENT_PAYLOAD_INVALID", "Submission identity does not match");
            AssessmentSkill skill = AssessmentSkill.valueOf(input.path("skill").asText());
            String transcript = null;
            JsonNode acoustic = null;
            if (skill == AssessmentSkill.SPEAKING) {
                byte[] audio = storage.download(bucket, input.path("objectKey").asText());
                if (audio.length < 44 || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F'
                        || audio[8] != 'W' || audio[9] != 'A' || audio[10] != 'V' || audio[11] != 'E')
                    return Outcome.permanentFailure("RECORDING_INVALID", "The file is not WAV audio");
                acoustic = mapper.readTree(speech.assess(audio, input.path("contentType").asText(), "en-US", null));
                transcript = acoustic.path("recognized_text").asText();
                if (transcript.isBlank() || !acoustic.path("pronunciation").isNumber()
                        || !acoustic.path("fluency").isNumber())
                    return Outcome.permanentFailure("SPEECH_EVIDENCE_MISSING",
                            "Acoustic evidence is incomplete; request human review");
            }
            var evidence = mapper.createObjectNode();
            evidence.set("task", mapper.readTree(input.path("taskSnapshot").asText()));
            evidence.put("response", skill == AssessmentSkill.WRITING ? input.path("answerText").asText() : transcript);
            if (acoustic != null) {
                var speechEvidence = mapper.createObjectNode();
                for (String field : java.util.List.of("accuracy", "fluency", "prosody", "pronunciation"))
                    speechEvidence.set(field, acoustic.path(field));
                speechEvidence.set("recording", acoustic.path("raw"));
                // Do not send provider raw responses or an unbounded phoneme list to an LLM.
                if (speechEvidence.path("recording").has("NBest"))
                    speechEvidence.remove("recording");
                evidence.set("speechEvidence", speechEvidence);
            }
            String raw = llm.generateStructured(AssessmentRubric.systemPrompt(skill),
                    mapper.writeValueAsString(evidence), 3000);
            String content = mapper.readTree(raw).path("content").asText("");
            String normalized = AssessmentRubric.validate(mapper, skill, content);
            writer.complete(targetId, input.path("revision").asInt(), normalized, transcript);
            return Outcome.succeeded(normalized);
        } catch (LlmException e) {
            return e.isRetryable() ? Outcome.transientFailure(e.getCode(), "The grading provider is unavailable")
                    : Outcome.permanentFailure(e.getCode(), "The grading provider rejected the request");
        } catch (SpeechAssessmentException e) {
            return e.isRetryable() ? Outcome.transientFailure(e.getCode(), "Speech assessment is unavailable")
                    : Outcome.permanentFailure(e.getCode(), "Speech could not be assessed");
        } catch (DomainException e) {
            return Outcome.permanentFailure(e.getCode(), e.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            return Outcome.permanentFailure("ASSESSMENT_PAYLOAD_INVALID", "The grading response is invalid");
        }
    }

    public void onGaveUp(UUID id, String code) {
        writer.fail(id, code);
    }
}
