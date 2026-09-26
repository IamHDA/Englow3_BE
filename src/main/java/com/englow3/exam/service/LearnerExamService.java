package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.result.*;
import com.englow3.exam.entity.*;

public interface LearnerExamService {
    Page<LearnerExamListItemResult> search(ExamType examType, CertificateType certificateType,
            CertificateVariant certificateVariant, TargetLevel targetLevel, String title, Pageable pageable);

    LearnerExamListItemResult detail(UUID examId);

    Page<ExamAttemptResult> attemptHistory(Pageable pageable);

    ExamAttemptResult start(UUID examId);

    LearnerExamPaperResult paperForAttempt(UUID attemptId);

    ExamAttemptResult submit(SubmitExamAttemptCommand command);

    LearnerExamListItemResult placementExam();

    ExamAttemptResult result(UUID attemptId);
}
