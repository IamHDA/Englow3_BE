package com.englow3.speaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.ai.client.SpeechAssessmentClient;
import com.englow3.ai.client.SpeechAssessmentException;
import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.api.AiJobHandler.Outcome;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One attempt at one assessment. Every branch here answers the same question twice - is this worth retrying, and does
 * the learner get told - and the two answers are deliberately not the same.
 */
class SpeechAssessmentHandlerTest {

    private final SpeechAssessmentClient speechClient = mock(SpeechAssessmentClient.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final SpeakingAssessmentWriter writer = mock(SpeakingAssessmentWriter.class);

    private final SpeechAssessmentHandler handler = new SpeechAssessmentHandler(speechClient, objectStorage, writer,
            new ObjectMapper());

    private final UUID attemptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "speakingBucket", "speaking");
        when(objectStorage.download(anyString(), anyString())).thenReturn(new byte[] { 1, 2, 3 });
    }

    private AiJob jobFor(String payload) {
        return AiJob.pending(AiJobType.SPEECH_ASSESSMENT, "SPEAKING_ATTEMPT", attemptId, "azure", "model", "v1",
                payload, UUID.randomUUID().toString(), (short) 3);
    }

    private AiJob validJob() {
        return jobFor("""
                {"speakingAttemptId":"%s","objectKey":"speaking/u/a.wav",
                 "contentType":"audio/wav","locale":"en-US","referenceText":"Hello there"}
                """.formatted(attemptId));
    }

    private Outcome run(AiJob job) {
        return handler.run(job.getId(), job.getTargetId(), job.getInputPayload());
    }

    @Test
    void storesTheAssessmentAndReportsSuccess() {
        when(speechClient.assess(any(), eq("audio/wav"), eq("en-US"), eq("Hello there")))
                .thenReturn("{\"recognized_text\":\"Hello there\",\"accuracy\":88}");

        Outcome outcome = run(validJob());

        assertThat(outcome.success()).isTrue();
        verify(writer).storeAssessment(eq(attemptId), any());
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * The rule worth pinning down. A retryable failure leaves the attempt alone: the learner is still waiting and the
     * work is still coming, so showing them "failed" before a retry that may well succeed is worse than the wait.
     */
    @Test
    void leavesTheLearnerWaitingWhenTheFailureIsWorthRetrying() {
        when(speechClient.assess(any(), anyString(), anyString(), anyString()))
                .thenThrow(new SpeechAssessmentException("SPEECH_SERVICE_UNREACHABLE", "timeout", true));

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isTrue();
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * A refusal is reported as final. Telling the learner waits for the queue, which alone knows the job is over - see
     * {@code tellsTheLearnerOnceTheJobHasGivenUp}.
     */
    @Test
    void reportsARefusalAsFinalWithoutTellingTheLearnerYet() {
        when(speechClient.assess(any(), anyString(), anyString(), anyString()))
                .thenThrow(new SpeechAssessmentException("SPEECH_ASSESSMENT_REJECTED", "415", false));

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isFalse();
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * The provider answered, but with something that is not a usable assessment. Repeating it produces the same answer,
     * so this ends here rather than three attempts later.
     */
    @Test
    void givesUpOnAnAnswerThatCannotBeRead() {
        when(speechClient.assess(any(), anyString(), anyString(), anyString())).thenReturn("<html>502</html>");

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("SPEECH_ASSESSMENT_UNREADABLE");
        verify(writer, never()).markFailed(any(), anyString());
    }

    /** Storage being unreachable says nothing about the recording, so it is always worth another go. */
    @Test
    void retriesWhenTheRecordingCannotBeRead() {
        when(objectStorage.download(anyString(), anyString())).thenThrow(new RuntimeException("connection reset"));

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.errorCode()).isEqualTo("SPEAKING_RECORDING_UNREADABLE");
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * A payload this handler did not write will never become one, and there is no attempt id in it to mark failed
     * either - so it ends immediately rather than being retried into the same dead end.
     */
    @Test
    void failsPermanentlyOnAPayloadItDoesNotRecognise() {
        Outcome outcome = run(jobFor("{\"somethingElse\":true}"));

        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("SPEECH_JOB_PAYLOAD_UNREADABLE");
        verify(speechClient, never()).assess(any(), anyString(), anyString(), anyString());
    }

    @Test
    void failsPermanentlyOnAPayloadThatIsNotJson() {
        Outcome outcome = run(jobFor("not json at all"));

        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("SPEECH_JOB_PAYLOAD_UNREADABLE");
    }

    /** An assessment that recognised nothing is not an assessment - a row of nulls would pretend otherwise. */
    @Test
    void failsWhenTheProviderRecognisedNoSpeech() {
        when(speechClient.assess(any(), anyString(), anyString(), anyString()))
                .thenReturn("{\"recognized_text\":\"   \"}");

        Outcome outcome = run(validJob());

        assertThat(outcome.errorCode()).isEqualTo("SPEECH_ASSESSMENT_EMPTY");
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * Telling the learner is not {@code run}'s job any more: it knew about one of the four ways a job ends. The worker
     * calls this once the queue has recorded the job as over, whichever way it got there.
     */
    @Test
    void tellsTheLearnerOnceTheJobHasGivenUp() {
        handler.onGaveUp(validJob().getTargetId(), "PROVIDER_DOWN");

        verify(writer).markFailed(attemptId, "PROVIDER_DOWN");
    }

    /**
     * Read from the job's own target, not its payload - so even a job whose payload could not be read still reaches the
     * learner it belongs to. That case used to leave them waiting with no way to be told.
     */
    @Test
    void reachesTheLearnerEvenWhenThePayloadWasUnreadable() {
        handler.onGaveUp(jobFor("not json at all").getTargetId(), "PAYLOAD_UNREADABLE");

        verify(writer).markFailed(attemptId, "PAYLOAD_UNREADABLE");
    }
}
