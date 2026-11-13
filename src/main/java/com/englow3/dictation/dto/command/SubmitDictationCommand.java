package com.englow3.dictation.dto.command;

import java.util.UUID;

public record SubmitDictationCommand(UUID sentenceId, String response) {
}
