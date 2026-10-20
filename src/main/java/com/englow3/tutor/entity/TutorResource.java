package com.englow3.tutor.entity;

import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import com.englow3.shared.error.ConflictException;

@Entity
@Table(name = "tutor_resources")
@Getter
public class TutorResource {
    @Id
    private UUID id;
    private UUID documentId;
    private int versionNumber;
    private String title;
    private String body;
    private String status;
    private UUID authorId;
    private String reviewNote;
    @Version
    private long rowVersion;

    protected TutorResource() {
    }

    public static TutorResource draft(String title, String body, UUID author) {
        TutorResource resource = new TutorResource();
        resource.id = UUID.randomUUID();
        resource.documentId = resource.id;
        resource.versionNumber = 1;
        resource.title = title.strip();
        resource.body = body.strip();
        resource.authorId = author;
        resource.status = "DRAFT";
        return resource;
    }

    public TutorResource revision(UUID author, int nextVersion) {
        TutorResource resource = draft(title, body, author);
        resource.documentId = documentId;
        resource.versionNumber = nextVersion;
        return resource;
    }

    public void edit(String title, String body, long expectedVersion) {
        if ((!"DRAFT".equals(status) && !"REJECTED".equals(status)) || rowVersion != expectedVersion)
            throw new ConflictException("TUTOR_RESOURCE_CONFLICT", "Refresh this draft before editing");
        this.title = title.strip();
        this.body = body.strip();
        status = "DRAFT";
        reviewNote = null;
    }

    public void submit() {
        if (!"DRAFT".equals(status))
            throw new ConflictException("TUTOR_RESOURCE_STATE", "Submit a draft for review");
        status = "SUBMITTED";
    }

    public void decide(String action, String note) {
        switch (action) {
            case "PUBLISH" -> {
                if (!"SUBMITTED".equals(status))
                    throw new ConflictException("TUTOR_RESOURCE_STATE", "Only submitted resources can be published");
                status = "PUBLISHED";
            }
            case "REJECT" -> {
                if (!"SUBMITTED".equals(status))
                    throw new ConflictException("TUTOR_RESOURCE_STATE", "Only submitted resources can be rejected");
                status = "REJECTED";
            }
            case "ARCHIVE" -> status = "ARCHIVED";
            default -> throw new ConflictException("TUTOR_RESOURCE_ACTION", "Unknown resource action");
        }
        reviewNote = note;
    }
}
