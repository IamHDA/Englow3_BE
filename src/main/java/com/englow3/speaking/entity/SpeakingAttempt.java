package com.englow3.speaking.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** One recording of one prompt, and what the assessment made of it. */
@Entity
@Table(name = "speaking_attempts")
@Getter
public class SpeakingAttempt {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "speaking_prompt_id", nullable = false, updatable = false)
    private UUID speakingPromptId;

    /**
     * The object key, not a URL. Every URL this system hands out is signed and expires, so storing one would store
     * something that stops working; the key is what the row actually means and a fresh URL is derived on each read.
     */
    @Column(name = "audio_object_key", nullable = false, updatable = false)
    private String audioObjectKey;

    @Column(name = "audio_content_type", nullable = false, updatable = false)
    private String audioContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpeakingAttemptStatus status;

    @Column(name = "accuracy_percent")
    private BigDecimal accuracyPercent;

    @Column(name = "fluency_percent")
    private BigDecimal fluencyPercent;

    @Column(name = "completeness_percent")
    private BigDecimal completenessPercent;

    @Column(name = "prosody_percent")
    private BigDecimal prosodyPercent;

    @Column(name = "pronunciation_percent")
    private BigDecimal pronunciationPercent;

    @Column(name = "recognized_text")
    private String recognizedText;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "assessed_at")
    private Instant assessedAt;

    protected SpeakingAttempt() {
    }

    /**
     * Created before the audio exists. The row comes first so the upload has somewhere to belong: a file that arrives
     * with no row is an orphan nobody will ever look at, while a row with no file is simply an abandoned attempt.
     */
    public static SpeakingAttempt awaitingUpload(UUID userId, UUID speakingPromptId, String audioObjectKey,
            String audioContentType) {
        SpeakingAttempt attempt = new SpeakingAttempt();
        attempt.id = UUID.randomUUID();
        attempt.userId = userId;
        attempt.speakingPromptId = speakingPromptId;
        attempt.audioObjectKey = audioObjectKey;
        attempt.audioContentType = audioContentType;
        attempt.status = SpeakingAttemptStatus.AWAITING_UPLOAD;
        return attempt;
    }

    /**
     * The learner says the upload is done. Only from AWAITING_UPLOAD, so submitting twice does not enqueue the same
     * recording twice - the second call is refused rather than quietly doubling the provider bill.
     */
    public void markQueued() {
        if (status != SpeakingAttemptStatus.AWAITING_UPLOAD) {
            throw new ConflictException("SPEAKING_ATTEMPT_NOT_AWAITING_UPLOAD",
                    "Only an attempt waiting for its recording can be submitted; this one is %s".formatted(status));
        }
        this.status = SpeakingAttemptStatus.QUEUED;
    }

    /**
     * Writes the scores. Every one is nullable because the provider itself returns them that way: prosody is absent
     * unless it was asked for, and a recording of silence has no accuracy. A missing score is shown as missing rather
     * than as a zero the learner did not earn.
     */
    public void recordAssessment(String recognizedText, BigDecimal accuracy, BigDecimal fluency,
            BigDecimal completeness, BigDecimal prosody, BigDecimal pronunciation, Instant now) {
        this.status = SpeakingAttemptStatus.ASSESSED;
        this.recognizedText = recognizedText;
        this.accuracyPercent = accuracy;
        this.fluencyPercent = fluency;
        this.completenessPercent = completeness;
        this.prosodyPercent = prosody;
        this.pronunciationPercent = pronunciation;
        this.assessedAt = now;
        this.errorCode = null;
    }

    public void recordFailure(String errorCode, Instant now) {
        this.status = SpeakingAttemptStatus.FAILED;
        this.errorCode = errorCode;
        this.assessedAt = now;
    }
}
