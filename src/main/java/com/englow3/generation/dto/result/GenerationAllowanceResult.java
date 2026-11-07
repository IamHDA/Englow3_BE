package com.englow3.generation.dto.result;

/**
 * Today's requests for the signed-in user. A failed request does not count.
 *
 * @param inProgress
 *            whether one of theirs is still running - a new one waits until it ends
 */
public record GenerationAllowanceResult(int limit, long used, long remaining, boolean inProgress) {
}
