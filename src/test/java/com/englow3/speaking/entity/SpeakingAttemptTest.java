package com.englow3.speaking.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.ConflictException;

class SpeakingAttemptTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    private static SpeakingAttempt awaitingUpload() {
        return SpeakingAttempt.awaitingUpload(UUID.randomUUID(), UUID.randomUUID(), "speaking/u/a.wav", "audio/wav");
    }

    @Test
    void startsWithSomewhereToPutTheRecordingAndNoScore() {
        SpeakingAttempt attempt = awaitingUpload();

        assertThat(attempt.getStatus()).isEqualTo(SpeakingAttemptStatus.AWAITING_UPLOAD);
        assertThat(attempt.getAudioObjectKey()).isEqualTo("speaking/u/a.wav");
        assertThat(attempt.getPronunciationPercent()).isNull();
        assertThat(attempt.getAssessedAt()).isNull();
    }

    @Test
    void queuesTheAssessmentOnceTheRecordingHasArrived() {
        SpeakingAttempt attempt = awaitingUpload();

        attempt.markQueued();

        assertThat(attempt.getStatus()).isEqualTo(SpeakingAttemptStatus.QUEUED);
    }

    /**
     * The case that stops a double bill. A client that submits twice must not have the same recording assessed twice,
     * and the refusal here is the second guard behind the queue's own idempotency key.
     */
    @Test
    void refusesASecondSubmissionOfTheSameRecording() {
        SpeakingAttempt attempt = awaitingUpload();
        attempt.markQueued();

        assertThatThrownBy(attempt::markQueued).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_ATTEMPT_NOT_AWAITING_UPLOAD");
    }

    @Test
    void keepsEveryScoreTheAssessmentReturned() {
        SpeakingAttempt attempt = awaitingUpload();
        attempt.markQueued();

        attempt.recordAssessment("She has been there", new BigDecimal("92.50"), new BigDecimal("88.00"),
                new BigDecimal("100.00"), new BigDecimal("79.25"), new BigDecimal("90.00"), NOW);

        assertThat(attempt.getStatus()).isEqualTo(SpeakingAttemptStatus.ASSESSED);
        assertThat(attempt.getRecognizedText()).isEqualTo("She has been there");
        assertThat(attempt.getProsodyPercent()).isEqualByComparingTo("79.25");
        assertThat(attempt.getAssessedAt()).isEqualTo(NOW);
    }

    /**
     * A score the provider did not measure stays null rather than becoming zero. Prosody is absent unless it was asked
     * for, and showing nought out of a hundred would report a failure that never happened.
     */
    @Test
    void leavesAnUnmeasuredScoreEmptyRatherThanZero() {
        SpeakingAttempt attempt = awaitingUpload();
        attempt.markQueued();

        attempt.recordAssessment("Hello", new BigDecimal("80.00"), null, null, null, null, NOW);

        assertThat(attempt.getAccuracyPercent()).isEqualByComparingTo("80.00");
        assertThat(attempt.getProsodyPercent()).isNull();
        assertThat(attempt.getFluencyPercent()).isNull();
    }

    /** A reassessment that succeeds clears the failure it is replacing. */
    @Test
    void dropsAnEarlierErrorWhenAScoreFinallyArrives() {
        SpeakingAttempt attempt = awaitingUpload();
        attempt.markQueued();
        attempt.recordFailure("SPEECH_SERVICE_UNREACHABLE", NOW);

        attempt.recordAssessment("Hello", new BigDecimal("80.00"), null, null, null, null, NOW);

        assertThat(attempt.getErrorCode()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(SpeakingAttemptStatus.ASSESSED);
    }

    @Test
    void recordsWhyAnAssessmentWillNotHappen() {
        SpeakingAttempt attempt = awaitingUpload();
        attempt.markQueued();

        attempt.recordFailure("SPEECH_ASSESSMENT_EMPTY", NOW);

        assertThat(attempt.getStatus()).isEqualTo(SpeakingAttemptStatus.FAILED);
        assertThat(attempt.getErrorCode()).isEqualTo("SPEECH_ASSESSMENT_EMPTY");
    }
}
