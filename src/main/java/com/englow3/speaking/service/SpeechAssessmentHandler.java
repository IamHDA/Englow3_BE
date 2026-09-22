package com.englow3.speaking.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.englow3.ai.client.SpeechAssessmentClient;
import com.englow3.ai.client.SpeechAssessmentException;
import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.service.AiJobHandler;
import com.englow3.shared.error.DomainException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Runs one queued assessment: fetch the recording, ask {@code ai_service} to score it, write the result.
 * <p>
 * Lives in {@code speaking} rather than in {@code ai} because it writes this module's tables, and one module writes a
 * table. The queue knows only the {@link AiJobHandler} interface.
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SpeechAssessmentHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(SpeechAssessmentHandler.class);

    private final SpeechAssessmentClient speechClient;
    private final ObjectStorageClient objectStorage;
    private final SpeakingAssessmentWriter writer;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.speaking-bucket}")
    private String speakingBucket;

    @Override
    public AiJobType handles() {
        return AiJobType.SPEECH_ASSESSMENT;
    }

    /**
     * Not transactional, deliberately. Downloading the audio and calling the provider are the slow parts, and holding a
     * database connection across them is what the queue exists to avoid. The two writes go through
     * {@link SpeakingAssessmentWriter}, each in a transaction of its own that lasts as long as a write.
     */
    @Override
    public Outcome run(AiJob job) {
        JsonNode request = readRequest(job);
        if (request == null) {
            return Outcome.permanentFailure("SPEECH_JOB_PAYLOAD_UNREADABLE",
                    "The job payload is not the shape this handler writes");
        }

        UUID attemptId = UUID.fromString(request.path("speakingAttemptId").asText());

        byte[] audio;
        try {
            audio = objectStorage.download(speakingBucket, request.path("objectKey").asText());
        } catch (RuntimeException storageFailure) {
            // Object storage being unreachable is worth another go; the recording itself is fine.
            log.warn("Could not read the recording for speaking attempt {}", attemptId, storageFailure);
            return Outcome.transientFailure("SPEAKING_RECORDING_UNREADABLE", storageFailure.getMessage());
        }

        try {
            String response = speechClient.assess(audio, request.path("contentType").asText(),
                    request.path("locale").asText(), request.path("referenceText").asText());
            writer.storeAssessment(attemptId, SpeechAssessmentParser.parse(objectMapper, response));

            return Outcome.succeeded(response);
        } catch (SpeechAssessmentException providerFailure) {
            return finish(attemptId, providerFailure.getCode(), providerFailure.getMessage(),
                    providerFailure.isRetryable());
        } catch (DomainException unusable) {
            // The provider answered, but with something that is not a usable assessment. Repeating it produces the
            // same answer, so this ends here rather than three attempts later.
            return finish(attemptId, unusable.getCode(), unusable.getMessage(), false);
        }
    }

    /**
     * A retryable failure leaves the attempt QUEUED. The learner is still waiting and the work is still coming; showing
     * them "failed" only to succeed forty seconds later would be worse than the wait. Only a final failure is written
     * where they can see it.
     */
    private Outcome finish(UUID attemptId, String code, String message, boolean retryable) {
        if (retryable) {
            return Outcome.transientFailure(code, message);
        }
        writer.markFailed(attemptId, code);

        return Outcome.permanentFailure(code, message);
    }

    private JsonNode readRequest(AiJob job) {
        try {
            JsonNode request = objectMapper.readTree(job.getInputPayload());
            return request.hasNonNull("speakingAttemptId") ? request : null;
        } catch (JsonProcessingException malformed) {
            log.error("AI job {} carries an unreadable payload", job.getId(), malformed);
            return null;
        }
    }
}
