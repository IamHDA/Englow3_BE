package com.englow3.tutor.dto.result;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record TutorMemoryResult(boolean enabled, @Schema(nullable = true) String content, int throughOrder,
        long remainingMessages, @Schema(nullable = true) UUID pendingMessageId, int batchSize) {
}
