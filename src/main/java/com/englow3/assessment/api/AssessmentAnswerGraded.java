package com.englow3.assessment.api;

import java.util.UUID;

/**
 * A learner's writing or speaking answer has just received its grade, from the AI or a reviewer. Published inside the
 * grading transaction and delivered to listeners after it commits.
 * <p>
 * Lives in the assessment module, which owns the fact; whoever cares listens. The assessment module neither knows nor
 * depends on who that is.
 *
 * @param skill
 *            WRITING or SPEAKING
 */
public record AssessmentAnswerGraded(UUID attemptId, UUID userId, UUID taskId, String skill) {
}
