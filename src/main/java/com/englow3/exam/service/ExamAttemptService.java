package com.englow3.exam.service;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.SaveExamDraftCommand;
import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.ExamDraftResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.ExamAttemptMode;

public interface ExamAttemptService {
    Optional<ExamAttemptResult> activeAttempt(UUID examId, ExamAttemptMode mode);

    ExamDraftResult draft(UUID id);

    ExamDraftResult saveDraft(SaveExamDraftCommand command);

    void finalizeExpired(UUID id);

    /** The browser is still on this full sitting. False when the sitting is no longer open. */
    boolean heartbeat(UUID attemptId);

    /** Ends a full sitting nobody has been at for the grace period, scoring what was saved. */
    void finalizeAbandoned(UUID id);

    Page<ExamAttemptResult> attemptHistory(Pageable pageable);

    ExamAttemptResult start(UUID examId);

    ExamAttemptResult start(StartExamAttemptCommand command);

    LearnerExamPaperResult paperForAttempt(UUID attemptId);

    ExamAttemptResult submit(SubmitExamAttemptCommand command);

    ExamAttemptResult result(UUID attemptId);
}
