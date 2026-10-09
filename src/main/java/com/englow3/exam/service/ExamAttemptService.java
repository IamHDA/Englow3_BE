package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.SaveExamDraftCommand;
import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.ExamDraftResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult;

public interface ExamAttemptService {
    ExamDraftResult draft(UUID id);

    ExamDraftResult saveDraft(SaveExamDraftCommand command);

    void finalizeExpired(UUID id);

    Page<ExamAttemptResult> attemptHistory(Pageable pageable);

    ExamAttemptResult start(UUID examId);

    ExamAttemptResult start(StartExamAttemptCommand command);

    LearnerExamPaperResult paperForAttempt(UUID attemptId);

    ExamAttemptResult submit(SubmitExamAttemptCommand command);

    ExamAttemptResult result(UUID attemptId);
}
