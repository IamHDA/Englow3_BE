package com.englow3.tutor.worker;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.SpeechAssessmentClient;
import com.englow3.ai.client.SpeechAssessmentException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.tutor.helper.TutorWav;
import com.englow3.tutor.repository.TutorRecordingRepository;
import com.englow3.tutor.service.TutorTranscriptionWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

/** Reads only the owned immutable recording, never an arbitrary URL from a job payload. */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TutorTranscriptionHandler implements AiJobHandler {
    private final TutorRecordingRepository recordingRepo;
    private final ObjectStorageClient storage;
    private final SpeechAssessmentClient speech;
    private final TutorTranscriptionWriter writer;
    private final ObjectMapper mapper;
    @Value("${app.tutor.audio-bucket:speaking}")
    private String bucket;

    @Override
    public String handles() {
        return "TUTOR_TRANSCRIPTION";
    }

    @Override
    public Outcome run(UUID jobId, UUID targetId, String payload) {
        var row = recordingRepo.findById(targetId).orElse(null);
        if (row == null || !"QUEUED".equals(row.getStatus()))
            return Outcome.permanentFailure("TUTOR_RECORDING_REMOVED", "Recording is no longer queued");
        try {
            var metadata = storage.metadata(bucket, row.getObjectKey());
            if (metadata.contentLength() != row.getAudioSize() || metadata.contentLength() > TutorWav.MAX_BYTES)
                return Outcome.permanentFailure("TUTOR_AUDIO_INVALID", "Recording metadata is invalid");
            byte[] audio = storage.download(bucket, row.getObjectKey());
            TutorWav.validate(audio);
            String response = speech.assess(audio, "audio/wav", row.getLocale(), null);
            String transcript = mapper.readTree(response).path("recognized_text").asText("").strip();
            if (transcript.isBlank() || transcript.length() > 4000)
                return Outcome.permanentFailure("TUTOR_TRANSCRIPT_EMPTY", "No usable speech was recognized");
            return Outcome.succeeded(mapper.writeValueAsString(java.util.Map.of("transcript", transcript)));
        } catch (SpeechAssessmentException failure) {
            return new Outcome(false, null, failure.getCode(), "Speech service could not transcribe this recording",
                    failure.isRetryable()).retryAfter(failure.getRetryAfter());
        } catch (JsonProcessingException malformed) {
            return Outcome.permanentFailure("TUTOR_TRANSCRIPT_INVALID", "Speech service returned invalid text");
        } catch (com.englow3.shared.error.DomainException invalid) {
            return Outcome.permanentFailure(invalid.getCode(), "Recording is invalid");
        }
    }

    @Override
    public void complete(UUID targetId, String input, String output) {
        try {
            writer.complete(targetId, mapper.readTree(output).path("transcript").asText());
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Invalid transcription result", invalid);
        }
    }

    @Override
    public void onGaveUp(UUID targetId, String code) {
        writer.failed(targetId, code);
    }
}
