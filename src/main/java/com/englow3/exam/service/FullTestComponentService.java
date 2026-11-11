package com.englow3.exam.service;

import java.util.UUID;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.dto.command.StartExamAttemptCommand.OpenAttempt;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.FullTestPartStartResult;

public interface FullTestComponentService {
    ExamAttemptResult startPaper(UUID sittingId, OpenAttempt onOpen);

    FullTestPartStartResult startAssessment(UUID sittingId, FullTest.Slot slot, UUID expectedTaskId, String contentType,
            Long contentLength);
}
