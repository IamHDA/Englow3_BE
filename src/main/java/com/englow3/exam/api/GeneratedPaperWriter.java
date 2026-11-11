package com.englow3.exam.api;

import java.util.List;
import java.util.UUID;

/**
 * How generated content becomes papers: drafts authored by the person who asked for them, built from the same JSON the
 * admin editor saves, then sent for review like any paper. Nothing written here reaches learners until an administrator
 * approves it.
 * <p>
 * Every method names its author or target explicitly: the generation worker runs with no signed-in user.
 */
public interface GeneratedPaperWriter {

    /**
     * A draft paper from the editor's authoring JSON ({@code {version, metadata, content}}), checked exactly as a save
     * from the editor is. Returns its id.
     */
    UUID createDraft(UUID authorId, String authoringJson);

    /** Stores audio for a draft paper and returns its key. Storage only - call it outside a transaction. */
    String uploadAudio(UUID examId, byte[] mp3);

    /** Attaches uploaded audio to the Listening part with this order number, while the paper is a draft. */
    void attachListeningAudio(UUID authorId, UUID examId, int partOrderNo, String objectKey);

    void submitForReview(UUID examId);

    /** A draft Academic full test of this paper and five tasks in slot order. Checked when it goes for review. */
    UUID createFullTestDraft(UUID authorId, String title, String description, UUID examId, List<UUID> taskIds);
}
