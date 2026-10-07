package com.englow3.speaking.dto.result;

import java.util.UUID;
import com.englow3.speaking.dto.command.CreateSpeakingPromptCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote,
        CreateSpeakingPromptCommand metadata) {
}
