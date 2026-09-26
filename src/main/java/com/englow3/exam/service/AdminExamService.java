package com.englow3.exam.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.exam.dto.command.*;
import com.englow3.exam.dto.result.*;

public interface AdminExamService {
    ExamResult create(CreateExamCommand command);

    Page<ExamListItemResult> search(SearchExamCommand command, Pageable pageable);

    ExamDetailResult detail(ExamDetailCommand command);

    ExamResult update(UpdateExamCommand command);

    ExamResult publish(PublishExamCommand command);

    ExamResult submitForReview(SubmitExamForReviewCommand command);

    ExamResult approve(ApproveExamCommand command);

    ExamResult reject(RejectExamCommand command);

    ExamResult archive(ArchiveExamCommand command);

    ExamMediaResult uploadMedia(UUID examId, MultipartFile file);

    ExamDetailResult replaceContent(UpdateExamContentCommand command);

    Page<QuestionBankItemResult> searchQuestionBank(SearchQuestionBankCommand command, Pageable pageable);
}
