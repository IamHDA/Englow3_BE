package com.englow3.tutor.dto.result;

import java.util.List;

public record TutorMessagePageResult(TutorConversationSummaryResult conversation, List<TutorMessageResult> messages,
        int page, int totalPages, long totalElements) {
}
