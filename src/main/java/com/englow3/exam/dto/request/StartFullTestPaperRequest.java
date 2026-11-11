package com.englow3.exam.dto.request;

import com.englow3.exam.dto.command.StartExamAttemptCommand;

public record StartFullTestPaperRequest(StartExamAttemptCommand.OpenAttempt onOpen) {
}
