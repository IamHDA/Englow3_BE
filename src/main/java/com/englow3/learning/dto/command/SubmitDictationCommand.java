package com.englow3.learning.dto.command;

import java.util.UUID;

public record SubmitDictationCommand(UUID sentenceId, String response) {
}
