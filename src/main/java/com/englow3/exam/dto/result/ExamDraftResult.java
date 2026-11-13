package com.englow3.exam.dto.result;

import java.time.Instant;
import java.util.List;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;

public record ExamDraftResult(List<SubmittedAnswer> answers, long version, Instant savedAt) {
}
