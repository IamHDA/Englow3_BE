package com.englow3.assessment.api;

import java.util.UUID;

/**
 * How generated content becomes Writing and Speaking tasks: drafts authored by the person who asked, checked as an
 * authoring request is, then sent for review like any task. Every method names its author or task explicitly - the
 * generation worker runs with no signed-in user.
 */
public interface GeneratedTaskWriter {

    /** A draft task from the authoring request JSON. Returns its id. */
    UUID createDraft(UUID authorId, String taskJson);

    /** Stores a PNG chart for the task and returns its key. Storage only - call it outside a transaction. */
    String uploadImage(UUID taskId, byte[] png);

    /** Attaches an uploaded chart, while the task can still take one. */
    void attachImage(UUID taskId, String objectKey);

    void submitForReview(UUID taskId);
}
