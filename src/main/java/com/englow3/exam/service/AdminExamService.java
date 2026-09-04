package com.englow3.exam.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.AdminAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminExamService {

    private final ExamRepository examRepo;
    private final AdminExamPaperQuery examPaperQuery;
    private final AdminAccess adminAccess;

    @Transactional
    public ExamResult create(CreateExamCommand command) {
        Exam exam = Exam.draft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore(), adminAccess.requireAdminId());

        return ExamResult.of(examRepo.save(exam));
    }

    @Transactional(readOnly = true)
    public Page<ExamListItemResult> search(SearchExamCommand command, Pageable pageable) {
        adminAccess.requireAdminId();

        return examRepo.search(command.status(), command.examType(), command.title(), pageable)
                .map(ExamListItemResult::of);
    }

    /** The whole paper with answer keys - what the detail screen renders and what its printed form uses. */
    @Transactional(readOnly = true)
    public ExamDetailResult detail(ExamDetailCommand command) {
        adminAccess.requireAdminId();

        return examPaperQuery.loadForAdmin(command.examId()).orElseThrow(() -> examNotFound(command.examId()));
    }

    /** No {@code save()}: the entity is managed, so the change flushes at commit. */
    @Transactional
    public ExamResult update(UpdateExamCommand command) {
        adminAccess.requireAdminId();

        Exam exam = requireExam(command.examId());
        exam.updateDraft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore());

        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult publish(PublishExamCommand command) {
        adminAccess.requireAdminId();

        Exam exam = requireExam(command.examId());
        exam.publish(examRepo.countSections(exam.getId()), examRepo.countQuestions(exam.getId()),
                examRepo.sumSectionScores(exam.getId()), Instant.now());

        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult archive(ArchiveExamCommand command) {
        adminAccess.requireAdminId();

        Exam exam = requireExam(command.examId());
        exam.archive();

        return ExamResult.of(exam);
    }

    private Exam requireExam(UUID examId) {
        return examRepo.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private static NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId));
    }
}
