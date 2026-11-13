package com.englow3.exam.dto.command;

import java.util.Set;
import java.util.UUID;

import com.englow3.exam.entity.ExamAttemptMode;

/**
 * What a learner asked for when opening a paper.
 *
 * @param partIds
 *            the parts a practice covers; ignored for a full attempt
 * @param timeLimitMinutes
 *            a practice's clock, or null for none; ignored for a full attempt, which runs on the paper's own
 * @param onOpen
 *            what to do when an attempt on this paper is already open and it is not the one asked for; null refuses
 *            with {@code ATTEMPT_IN_PROGRESS} so the learner can choose
 */
public record StartExamAttemptCommand(UUID examId, ExamAttemptMode mode, Set<UUID> partIds, Integer timeLimitMinutes,
        OpenAttempt onOpen) {

    /** Keep the attempt that is already open, or finalize it from its saved answers and start the one asked for. */
    public enum OpenAttempt {
        RESUME, REPLACE
    }

    public static StartExamAttemptCommand full(UUID examId) {
        return new StartExamAttemptCommand(examId, ExamAttemptMode.FULL, Set.of(), null, null);
    }
}
