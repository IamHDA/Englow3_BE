package com.englow3.tutor.dto.command;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public record TutorActionCommand(@NotNull UUID clientMessageId) {
}
