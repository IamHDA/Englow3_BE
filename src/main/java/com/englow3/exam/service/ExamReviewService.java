package com.englow3.exam.service;

import com.englow3.exam.dto.command.ApproveExamCommand;
import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.RejectExamCommand;
import com.englow3.exam.dto.command.SubmitExamForReviewCommand;
import com.englow3.exam.dto.result.ExamResult;

public interface ExamReviewService {
    ExamResult publish(PublishExamCommand command);

    ExamResult submitForReview(SubmitExamForReviewCommand command);

    ExamResult approve(ApproveExamCommand command);

    ExamResult reject(RejectExamCommand command);

    ExamResult archive(ArchiveExamCommand command);
}
