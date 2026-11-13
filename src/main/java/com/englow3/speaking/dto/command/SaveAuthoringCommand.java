package com.englow3.speaking.dto.command;

import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateSpeakingPromptCommand metadata) {
}
