package com.englow3.tutor.dto.result;

import java.util.List;

/** A thread with its turns, in order. */
public record TutorConversationResult(TutorConversationSummaryResult conversation, List<TutorMessageResult> messages) {
}
