package com.englow3.tutor.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** Original answer snapshots are immutable; later approved corrections are attached as review metadata. */
@Entity
@Table(name = "tutor_reply_versions")
@Getter
public class TutorReplyVersion {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID messageId;
    private int revision;
    private String status;
    private String content;
    private String model;
    private String errorCode;
    private Integer inputTokens;
    private Integer outputTokens;
    private Instant answeredAt;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String sourcesJson = "[]";
    private String correction;

    protected TutorReplyVersion() {
    }

    public static TutorReplyVersion snapshot(TutorMessage message) {
        TutorReplyVersion version = new TutorReplyVersion();
        version.id = UUID.randomUUID();
        version.messageId = message.getId();
        version.revision = message.getRevision();
        version.status = message.getStatus().name();
        version.content = message.getContent();
        version.model = message.getModel();
        version.errorCode = message.getErrorCode();
        version.inputTokens = message.getInputTokens();
        version.outputTokens = message.getOutputTokens();
        version.answeredAt = message.getAnsweredAt();
        version.sourcesJson = message.getSourcesJson();
        version.correction = message.getCorrection();
        return version;
    }
}
