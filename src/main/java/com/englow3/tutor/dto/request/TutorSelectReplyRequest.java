package com.englow3.tutor.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record TutorSelectReplyRequest(@NotNull UUID clientMessageId, @Min(0) int revision) {
}
