package com.englow3.assessment.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Table(name = "assessment_attempts")
@Getter
public class AssessmentAttempt {
    @Id
    private UUID id;
    private UUID userId;
    private UUID taskId;
    private UUID clientKey;
    @Enumerated(EnumType.STRING)
    private AssessmentSkill skill;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String taskSnapshot;
    @Column(columnDefinition = "text")
    private String answerText;
    private String audioObjectKey;
    private String audioContentType;
    private Long audioContentLength;
    @Enumerated(EnumType.STRING)
    private AssessmentAttemptStatus status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String report;
    @Column(columnDefinition = "text")
    private String recognizedText;
    private String source;
    private String errorCode;
    private int gradingRevision;
    @Version
    private long version;
    @org.hibernate.annotations.CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
    private Instant submittedAt;
    private Instant assessedAt;

    protected AssessmentAttempt() {
    }

    public static AssessmentAttempt draft(UUID id, UUID user, UUID task, UUID clientKey, AssessmentSkill skill,
            String snapshot, String audioKey, String contentType, Long length) {
        AssessmentAttempt a = new AssessmentAttempt();
        a.id = id;
        a.userId = user;
        a.taskId = task;
        a.clientKey = clientKey;
        a.skill = skill;
        a.taskSnapshot = snapshot;
        a.answerText = "";
        a.audioObjectKey = audioKey;
        a.audioContentType = contentType;
        a.audioContentLength = length;
        a.status = AssessmentAttemptStatus.DRAFT;
        return a;
    }

    public void saveAnswer(String text, long expectedVersion) {
        require(AssessmentAttemptStatus.DRAFT);
        if (version != expectedVersion) {
            throw new ConflictException("DRAFT_CHANGED", "The draft changed in another tab. Reload before saving");
        }
        answerText = text == null ? "" : text;
    }

    public void submit(boolean automatic, Instant now) {
        require(AssessmentAttemptStatus.DRAFT);
        status = automatic ? AssessmentAttemptStatus.QUEUED : AssessmentAttemptStatus.NEEDS_REVIEW;
        submittedAt = now;
        gradingRevision++;
    }

    public void sealRecording(String key) {
        require(AssessmentAttemptStatus.DRAFT);
        if (skill == AssessmentSkill.SPEAKING) {
            audioObjectKey = key;
        }
    }

    public void retry(boolean automatic) {
        require(AssessmentAttemptStatus.FAILED);
        status = automatic ? AssessmentAttemptStatus.QUEUED : AssessmentAttemptStatus.NEEDS_REVIEW;
        errorCode = null;
        gradingRevision++;
    }

    public void requestReview() {
        require(AssessmentAttemptStatus.FAILED);
        status = AssessmentAttemptStatus.NEEDS_REVIEW;
        errorCode = null;
    }

    public boolean finishAi(int revision, String report, String transcript, Instant now) {
        if (status != AssessmentAttemptStatus.QUEUED || gradingRevision != revision) {
            return false;
        }
        this.report = report;
        recognizedText = transcript;
        source = "AI";
        status = AssessmentAttemptStatus.COMPLETED;
        assessedAt = now;
        errorCode = null;
        return true;
    }

    public void finishHuman(String report, String transcript, Instant now, boolean admin) {
        if (status != AssessmentAttemptStatus.NEEDS_REVIEW && status != AssessmentAttemptStatus.FAILED
                && !(admin && status == AssessmentAttemptStatus.COMPLETED)) {
            throw new ConflictException("ATTEMPT_NOT_REVIEWABLE", "The submission is not available for review");
        }
        this.report = report;
        recognizedText = transcript;
        source = "HUMAN";
        status = AssessmentAttemptStatus.COMPLETED;
        assessedAt = now;
        errorCode = null;
    }

    public void fail(String code) {
        if (status == AssessmentAttemptStatus.QUEUED) {
            status = AssessmentAttemptStatus.FAILED;
            errorCode = code;
        }
    }

    private void require(AssessmentAttemptStatus expected) {
        if (status != expected) {
            throw new ConflictException("ATTEMPT_STATE_INVALID", "The submission is " + status);
        }
    }
}
