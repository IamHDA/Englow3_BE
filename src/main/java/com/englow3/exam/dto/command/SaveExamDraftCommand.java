package com.englow3.exam.dto.command;

import java.util.List;
import java.util.UUID;

public record SaveExamDraftCommand(UUID attemptId, long version,
        List<SubmitExamAttemptCommand.SubmittedAnswer> answers) {
}
