package com.englow3.tutor.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "tutor_recordings")
@Getter
public class TutorRecording {
    @Id
    private UUID id;
    private UUID userId;
    private UUID clientMessageId;
    private String objectKey;
    private long audioSize;
    private String fingerprint;
    private String locale;
    private String status;
    private String transcript;
    private String errorCode;
    @Column(insertable = false, updatable = false)
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    private Instant createdAt;

    protected TutorRecording() {
    }

    public static TutorRecording receiving(UUID user, UUID clientId, String fingerprint, long size, String locale) {
        TutorRecording recording = new TutorRecording();
        recording.id = UUID.randomUUID();
        recording.userId = user;
        recording.clientMessageId = clientId;
        recording.objectKey = "tutor/" + user + "/" + recording.id + ".wav";
        recording.fingerprint = fingerprint;
        recording.audioSize = size;
        recording.locale = locale;
        recording.status = "UPLOADING";
        return recording;
    }

    public void queued() {
        if ("UPLOADING".equals(status))
            status = "QUEUED";
    }

    public void ready(String text) {
        if ("QUEUED".equals(status)) {
            status = "READY";
            transcript = text;
            errorCode = null;
        }
    }

    public void failed(String code) {
        if ("QUEUED".equals(status) || "UPLOADING".equals(status)) {
            status = "FAILED";
            errorCode = code;
        }
    }

    public void discard() {
        status = "DELETED";
        transcript = null;
        errorCode = null;
    }
}
