package com.englow3.tutor.dto.command;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record TutorReviewCommand(@NotBlank @Size(max = 30) String action, UUID assignedTo,
        @Size(max = 2000) String note, @Size(max = 8000) String correction) {
}
