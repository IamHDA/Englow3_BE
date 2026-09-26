package com.englow3.speaking.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.englow3.ai.client.SpeechAssessmentClient;
import com.englow3.ai.client.SpeechAssessmentException;
import com.englow3.ai.api.AiJobHandler;
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
    public String handles() {
        return "SPEECH_ASSESSMENT";
    }

    /**
     * Not transactional, deliberately. Downloading the audio and calling the provider are the slow parts, and holding a
     * database connection across them is what the queue exists to avoid. The two writes go through
     * {@link SpeakingAssessmentWriter}, each in a transaction of its own that lasts as long as a write.
     */
    @Override
    public Outcome run(UUID jobId, UUID targetId, String inputPayload) {
        JsonNode request = readRequest(jobId, inputPayload);
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
            return finish(providerFailure.getCode(), providerFailure.getMessage(), providerFailure.isRetryable());
        } catch (DomainException unusable) {
            // The provider answered, but with something that is not a usable assessment. Repeating it produces the
            // same answer, so this ends here rather than three attempts later.
            return finish(unusable.getCode(), unusable.getMessage(), false);
        }
    }

    /**
     * Says what kind of failure this was, and nothing more. Whether the learner is told is decided once the queue has
     * recorded it - see {@link #onGaveUp} - because only the queue knows whether a retry is still coming. Deciding it
     * here covered one of the four ways a job can end and left the attempt waiting on the other three.
     */
    private static Outcome finish(String code, String message, boolean retryable) {
        return retryable ? Outcome.transientFailure(code, message) : Outcome.permanentFailure(code, message);
    }

    /**
     * Nothing more is coming, so the learner is told. Read from the job's own target rather than its payload, which is
     * what lets this work even for a payload that could not be read.
     */
    @Override
    public void onGaveUp(UUID targetId, String errorCode) {
        writer.markFailed(targetId, errorCode);
    }

    private JsonNode readRequest(UUID jobId, String inputPayload) {
        try {
            JsonNode request = objectMapper.readTree(inputPayload);
            return request.hasNonNull("speakingAttemptId") ? request : null;
        } catch (JsonProcessingException malformed) {
            log.error("AI job {} carries an unreadable payload", jobId, malformed);
            return null;
        }
    }
}
