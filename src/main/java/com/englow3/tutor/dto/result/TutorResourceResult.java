package com.englow3.tutor.dto.result;

import java.util.UUID;
import com.englow3.tutor.entity.TutorResource;

public record TutorResourceResult(UUID id, UUID documentId, int versionNumber, String title, String body, String status,
        UUID authorId, String reviewNote, long rowVersion) {
    public static TutorResourceResult of(TutorResource resource) {
        return new TutorResourceResult(resource.getId(), resource.getDocumentId(), resource.getVersionNumber(),
                resource.getTitle(), resource.getBody(), resource.getStatus(), resource.getAuthorId(),
                resource.getReviewNote(), resource.getRowVersion());
    }
}
