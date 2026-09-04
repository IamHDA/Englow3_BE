package com.englow3.exam.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.repository.ExamContentTotals;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.shared.error.NotFoundException;
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

    /**
     * The counts come from one extra query for the whole page rather than one per row - a lookup per row is an N+1 by
     * another name.
     */
    @Transactional(readOnly = true)
    public Page<ExamListItemResult> search(SearchExamCommand command, Pageable pageable) {
        adminAccess.requireAdminId();

        Page<Exam> page = examRepo.search(command.status(), command.examType(), command.title(), pageable);
        Map<UUID, ExamContentTotals> totals = totalsFor(page.getContent().stream().map(Exam::getId).toList());

        return page.map(exam -> {
            ExamContentTotals row = totals.get(exam.getId());
            return ExamListItemResult.of(exam, row.getSectionCount(), row.getQuestionCount());
        });
    }

    /** No {@code save()}: the entity is managed, so the status change flushes at commit. */
    @Transactional
    public ExamResult publish(PublishExamCommand command) {
        adminAccess.requireAdminId();

        Exam exam = requireExam(command.examId());
        ExamContentTotals totals = examRepo.contentTotals(List.of(exam.getId())).getFirst();
        exam.publish(totals.getSectionCount(), totals.getQuestionCount(), totals.getSectionsRawTotal(), Instant.now());

        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult archive(ArchiveExamCommand command) {
        adminAccess.requireAdminId();

        Exam exam = requireExam(command.examId());
        exam.archive();

        return ExamResult.of(exam);
    }

    /** An empty page must not reach the query: {@code in ()} is not valid SQL. */
    private Map<UUID, ExamContentTotals> totalsFor(List<UUID> examIds) {
        return examIds.isEmpty() ? Map.of()
                : examRepo.contentTotals(examIds).stream().collect(toMap(ExamContentTotals::getExamId, identity()));
    }

    private Exam requireExam(UUID examId) {
        return examRepo.findById(examId)
                .orElseThrow(() -> new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId)));
    }
}
