package com.englow3.translation.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.translation.entity.TranslationAttemptStatus;

/**
 * One attempt and its grade.
 *
 * @param referenceEn
 *            shown once the attempt is no longer waiting - the learner has answered, so the model answer can no longer
 *            stand in for their own
 * @param feedback
 *            null until graded
 */
public record TranslationAttemptResult(UUID id, UUID setId, UUID itemId, String sourceVi, String response,
        TranslationAttemptStatus status, Integer score, Feedback feedback, String errorCode, String referenceEn,
        String notes, Instant createdAt, Instant gradedAt) {

    public record Feedback(String verdict, Parts parts, String summary, String improved, List<Mistake> errors) {
    }

    public record Parts(int meaning, int grammar, int vocabulary, int naturalness) {
    }

    public record Mistake(String type, String original, String correction, String explanation) {
    }
}
