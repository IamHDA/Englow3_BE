package com.englow3.tutor.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record TutorBranchRequest(@NotNull UUID clientMessageId, @NotBlank @Size(max = 4000) String message,
        Boolean personalized) {
}
