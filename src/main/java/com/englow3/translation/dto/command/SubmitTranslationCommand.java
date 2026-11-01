package com.englow3.translation.dto.command;

import java.util.UUID;

public record SubmitTranslationCommand(UUID itemId, String response) {
}
