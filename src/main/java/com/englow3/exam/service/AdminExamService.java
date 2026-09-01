package com.englow3.exam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.user.service.AdminAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminExamService {

    private final ExamRepository examRepo;
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
}
