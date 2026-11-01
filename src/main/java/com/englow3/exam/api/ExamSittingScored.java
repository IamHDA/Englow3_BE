package com.englow3.exam.api;

import java.util.UUID;

/**
 * A learner's sitting has just been scored. Published inside the scoring transaction and delivered to listeners after
 * it commits, so a listener only ever hears about a score that is really there.
 * <p>
 * Lives in the exam module, which owns the fact; whoever cares listens. The exam module neither knows nor depends on
 * who that is.
 *
 * @param full
 *            the whole paper rather than a practice of chosen parts
 */
public record ExamSittingScored(UUID attemptId, UUID userId, UUID examId, boolean full) {
}
