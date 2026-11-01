package com.englow3.roadmap.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * One measurement by the three-part level check: a paper (vocabulary/grammar and listening) taken in the exam module
 * and a spoken answer graded in the assessment module. This row holds the two sittings' ids and, as they are graded,
 * each part's band; the combined band is written once and with the weights that produced it.
 */
@Entity
@Table(name = "level_checks")
@Getter
public class LevelCheck extends BasePersistedEntity {

    private UUID userId;
    private UUID roadmapId;
    private UUID milestoneId;
    @Enumerated(EnumType.STRING)
    private LevelCheckPurpose purpose;
    private UUID examId;
    private UUID speakingTaskId;
    private UUID examAttemptId;
    private UUID speakingAttemptId;
    private boolean speakingSkipped;
    @Enumerated(EnumType.STRING)
    private CefrLevel vocabLevel;
    @Enumerated(EnumType.STRING)
    private CefrLevel listeningLevel;
    @Enumerated(EnumType.STRING)
    private CefrLevel speakingLevel;
    @Enumerated(EnumType.STRING)
    private CefrLevel resultLevel;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String weights;
    @Enumerated(EnumType.STRING)
    private LevelCheckStatus status;
    @Version
    private long version;
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
    private Instant completedAt;

    protected LevelCheck() {
    }

    /**
     * @param speakingTaskId
     *            the spoken task to answer; null when none is published, in which case the check measures the two
     *            written parts only
     */
    public static LevelCheck start(UUID id, UUID userId, LevelCheckPurpose purpose, UUID roadmapId, UUID milestoneId,
            UUID examId, UUID speakingTaskId) {
        if ((purpose == LevelCheckPurpose.MILESTONE) != (milestoneId != null)) {
            throw new BadRequestException("LEVEL_CHECK_PURPOSE_INVALID",
                    "A milestone level check belongs to a milestone, and only then");
        }
        LevelCheck check = new LevelCheck();
        check.id = Objects.requireNonNull(id);
        check.userId = Objects.requireNonNull(userId);
        check.purpose = purpose;
        check.roadmapId = roadmapId;
        check.milestoneId = milestoneId;
        check.examId = Objects.requireNonNull(examId);
        check.speakingTaskId = speakingTaskId;
        check.speakingSkipped = speakingTaskId == null;
        check.status = LevelCheckStatus.IN_PROGRESS;
        return check;
    }

    /** The learner's sitting of the paper. Replaceable until the paper's parts are scored. */
    public void attachExamAttempt(UUID attemptId) {
        requireNotCompleted();
        if (vocabLevel != null && !attemptId.equals(examAttemptId)) {
            throw new ConflictException("LEVEL_CHECK_PAPER_DONE", "The paper of this level check is already scored");
        }
        examAttemptId = Objects.requireNonNull(attemptId);
    }

    /** The learner's spoken answer. Replaceable until graded, so a failed grading can be answered again. */
    public void attachSpeakingAttempt(UUID attemptId) {
        requireNotCompleted();
        if (speakingTaskId == null) {
            throw new BadRequestException("LEVEL_CHECK_NO_SPEAKING", "This level check has no spoken part");
        }
        if (speakingLevel != null) {
            throw new ConflictException("LEVEL_CHECK_SPEAKING_DONE", "The spoken part is already graded");
        }
        speakingAttemptId = Objects.requireNonNull(attemptId);
        speakingSkipped = false;
    }

    /** No microphone, or grading failed and the learner would rather not wait: measure the written parts only. */
    public void skipSpeaking() {
        requireNotCompleted();
        if (speakingLevel != null) {
            throw new ConflictException("LEVEL_CHECK_SPEAKING_DONE", "The spoken part is already graded");
        }
        speakingSkipped = true;
    }

    public void recordWrittenParts(CefrLevel vocab, CefrLevel listening) {
        requireNotCompleted();
        vocabLevel = Objects.requireNonNull(vocab);
        listeningLevel = Objects.requireNonNull(listening);
        status = LevelCheckStatus.AWAITING_SPEAKING;
    }

    public void recordSpeaking(CefrLevel level) {
        requireNotCompleted();
        speakingLevel = Objects.requireNonNull(level);
    }

    public boolean hasWrittenParts() {
        return vocabLevel != null && listeningLevel != null;
    }

    public boolean isReadyToComplete() {
        return hasWrittenParts() && (speakingLevel != null || speakingSkipped);
    }

    /** The combined band, with the weights that produced it kept beside it. */
    public void complete(CefrLevel result, String appliedWeights, Instant now) {
        requireNotCompleted();
        if (!isReadyToComplete()) {
            throw new ConflictException("LEVEL_CHECK_INCOMPLETE",
                    "A level check needs its written parts, and its spoken part unless skipped");
        }
        if (result == null || appliedWeights == null || appliedWeights.isBlank()) {
            throw new BadRequestException("LEVEL_CHECK_RESULT_INVALID", "A result needs a band and its weights");
        }
        resultLevel = result;
        weights = appliedWeights;
        status = LevelCheckStatus.COMPLETED;
        completedAt = now;
    }

    private void requireNotCompleted() {
        if (status == LevelCheckStatus.COMPLETED) {
            throw new ConflictException("LEVEL_CHECK_COMPLETED", "This level check is finished");
        }
    }
}
