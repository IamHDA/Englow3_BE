package com.englow3.assessment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Writing and Speaking answers as IELTS bands, and the facts about a task, for the exam module's full test. Assessment
 * owns both the grade and what a task is, so the full test asks here rather than reading those tables. Plain values
 * only.
 */
public interface AssessmentBandReader {

    /** Empty when the attempt does not exist or belongs to someone else. */
    Optional<Answer> read(UUID attemptId, UUID ownerId);

    /**
     * The learner's most recent submitted answer to {@code taskId} started at or after {@code since}, graded or not. A
     * draft is not an answer.
     */
    Optional<Answer> latestAnswer(UUID taskId, UUID ownerId, Instant since);

    /** Empty when there is no such task. */
    Optional<Task> task(UUID taskId);

    /**
     * @param submittedAt
     *            when the learner handed it in; what a deadline is measured against
     * @param band
     *            mean of the four criteria on the 0-9 scale, in half bands; null until graded
     */
    record Answer(UUID attemptId, UUID taskId, Instant submittedAt, boolean completed, BigDecimal band) {
    }

    /**
     * @param skill
     *            WRITING or SPEAKING
     * @param taskType
     *            TASK_1, TASK_2, PART_1, PART_2 or PART_3
     * @param certificateType
     *            IELTS, TOEIC or null for general English
     */
    record Task(UUID id, String skill, String taskType, String certificateType, boolean published, boolean hasImage) {
    }
}
