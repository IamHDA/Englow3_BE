package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.ai.client.SpeechAssessmentClient;
import com.englow3.ai.service.AiJobWorkerQueue;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.englow3.tutor.repository.TutorRecordingRepository;
import com.englow3.tutor.service.TutorTranscriptionWriter;
import com.englow3.tutor.service.impl.TutorAudioServiceImpl;
import com.englow3.tutor.worker.TutorTranscriptionHandler;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class TutorAudioIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    TutorRecordingRepository recordingRepo;
    @Autowired
    UserDirectory users;
    @Autowired
    AiJobQueue jobs;
    @Autowired
    AiJobWorkerQueue worker;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    TutorTranscriptionWriter writer;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    com.englow3.tutor.service.TutorPolicyService policy;
    private final ObjectStorageClient storage = mock(ObjectStorageClient.class);
    private final SpeechAssessmentClient speech = mock(SpeechAssessmentClient.class);
    private TutorAudioServiceImpl audio;
    private UUID user;

    @BeforeEach
    void setup() {
        user = new LearnerFixture(jdbc).learner();
        SignedIn.as(jdbc, user);
        audio = new TutorAudioServiceImpl(recordingRepo, users, jobs, storage, transactions, new ObjectMapper(),
                Clock.systemUTC(), policy);
        ReflectionTestUtils.setField(audio, "enabled", true);
        ReflectionTestUtils.setField(audio, "aiEnabled", true);
        ReflectionTestUtils.setField(audio, "bucket", "speaking");
    }

    @AfterEach
    void cleanup() {
        SignedIn.out();
    }

    @Test
    void duplicateUploadTranscribesOnceAndDiscardScrubsWithoutRefundingUsage() {
        UUID key = UUID.randomUUID();
        byte[] wav = wav();
        var first = audio.upload(key, "en-US", wav);
        assertThat(first.status()).isEqualTo("QUEUED");
        assertThat(audio.upload(key, "en-US", wav).id()).isEqualTo(first.id());
        verify(storage, times(1)).upload(eq("speaking"), contains(first.id().toString()), any(InputStream.class),
                eq((long) wav.length), eq("audio/wav"));
        when(storage.metadata(anyString(), anyString()))
                .thenReturn(new ObjectStorageClient.StoredObjectMetadata(wav.length, "audio/wav"));
        when(storage.download(anyString(), anyString())).thenReturn(wav);
        when(speech.assess(any(), eq("audio/wav"), eq("en-US"), isNull()))
                .thenReturn("{\"recognized_text\":\"Explain the past perfect.\"}");
        var handler = new TutorTranscriptionHandler(recordingRepo, storage, speech, writer, new ObjectMapper());
        ReflectionTestUtils.setField(handler, "bucket", "speaking");
        var job = worker.claimBatch(1000, List.of("TUTOR_TRANSCRIPTION"), Duration.ofMinutes(3)).stream()
                .filter(row -> row.getTargetId().equals(first.id())).findFirst().orElseThrow();
        var outcome = handler.run(job.getId(), first.id(), job.getInputPayload());
        assertThat(outcome.success()).isTrue();
        worker.record(job.getId(), job.getStartedAt(), outcome,
                () -> handler.complete(first.id(), job.getInputPayload(), outcome.outputPayload()));
        assertThat(audio.receipt(key).transcript()).isEqualTo("Explain the past perfect.");
        audio.discard(first.id());
        writer.complete(first.id(), "Late answer must be ignored");
        assertThat(audio.receipt(key).status()).isEqualTo("DELETED");
        assertThat(audio.receipt(key).transcript()).isNull();
        assertThat(jobs.usage(user).used()).isEqualTo(1);
        assertThat(jdbc
                .sql("select output_payload is null and input_payload = '{}'::jsonb from ai_jobs where target_id = :id")
                .param("id", first.id()).query(Boolean.class).single()).isTrue();
    }

    @Test
    void recordingOwnershipAndFingerprintAreEnforced() {
        UUID key = UUID.randomUUID();
        var first = audio.upload(key, "en-US", wav());
        assertThatThrownBy(() -> audio.upload(key, "vi-VN", wav())).hasMessageContaining("another file");
        SignedIn.as(jdbc, new LearnerFixture(jdbc).learner());
        assertThatThrownBy(() -> audio.receipt(key)).hasMessageContaining("not found");
        assertThatThrownBy(() -> audio.discard(first.id())).hasMessageContaining("not found");
        verify(storage, never()).delete(anyString(), anyString());
    }

    @Test
    void disablingAiRefusesNewUploadsButStillAllowsErasingAnAcceptedRecording() {
        var first = audio.upload(UUID.randomUUID(), "en-US", wav());
        ReflectionTestUtils.setField(audio, "aiEnabled", false);
        assertThatThrownBy(() -> audio.upload(UUID.randomUUID(), "en-US", wav())).hasMessageContaining("unavailable");
        audio.discard(first.id());
        verify(storage).delete(eq("speaking"), contains(first.id().toString()));
    }

    @Test
    void uploadFailureIsTerminalAndNeverEnqueuesProviderWork() {
        doThrow(new IllegalStateException("Storage unavailable")).when(storage).upload(anyString(), anyString(), any(),
                anyLong(), anyString());
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> audio.upload(key, "en-US", wav())).hasMessageContaining("unavailable");
        assertThat(audio.receipt(key).status()).isEqualTo("FAILED");
        assertThat(jobs.usage(user).used()).isZero();
    }

    static byte[] wav() {
        byte[] bytes = new byte[32044];
        var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(bytes.length - 8)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(32000);
        return bytes;
    }
}
