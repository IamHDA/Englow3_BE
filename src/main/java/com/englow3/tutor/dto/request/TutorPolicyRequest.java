package com.englow3.tutor.dto.request;

import java.util.List;
import jakarta.validation.constraints.*;

public record TutorPolicyRequest(@NotNull Boolean accepting, @NotNull @Size(max = 4) List<String> allowedModes,
        @Min(0) long version, @NotBlank @Size(min = 5, max = 500) String reason) {
}
