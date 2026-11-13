package com.englow3.dictation.dto.result;

import java.util.List;

/**
 * The lines a learner keeps getting wrong, ready to practise again.
 * <p>
 * Across every lesson rather than within one. "What do I keep getting wrong" is a question about the learner, not about
 * a lesson, and the ten worst lines in one lesson are usually not the ten worth practising.
 */
public record MistakeQueueResult(List<MistakeSentenceResult> sentences) {
}
