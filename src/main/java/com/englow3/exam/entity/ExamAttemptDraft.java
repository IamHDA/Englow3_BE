package com.englow3.exam.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "exam_attempt_drafts")
@Getter
public class ExamAttemptDraft {
    @Id
    private UUID attemptId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String answers;
    @Column(nullable = false)
    private long revision;
    @Column(nullable = false)
    private Instant savedAt;

    protected ExamAttemptDraft() {
    }

    public static ExamAttemptDraft empty(UUID id, Instant now) {
        ExamAttemptDraft d = new ExamAttemptDraft();
        d.attemptId = id;
        d.answers = "[]";
        d.savedAt = now;
        return d;
    }

    public void replace(long expected, String answers, Instant now) {
        if (expected != revision) {
            throw new ConflictException("EXAM_DRAFT_CHANGED", "Another tab changed the answers. Reload the draft");
        }
        this.answers = answers;
        this.savedAt = now;
        revision++;
    }
}
