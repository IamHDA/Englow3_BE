package com.englow3.exam.service.impl;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.result.LearnerExamListItemResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.service.LearnerExamService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Published exam catalogue and placement lookup. */
@Service
@RequiredArgsConstructor
public class LearnerExamServiceImpl implements LearnerExamService {

    private final ExamRepository examRepo;
    private final ExamAttemptRepository attemptRepo;
    private final UserDirectory userDirectory;

    @Transactional(readOnly = true)
    public Page<LearnerExamListItemResult> search(ExamType examType, CertificateType certificateType,
            CertificateVariant certificateVariant, TargetLevel targetLevel, String title, Pageable pageable) {
        Page<Exam> page = examRepo.searchCatalogue(ExamStatus.PUBLISHED, examType, certificateType, certificateVariant,
                targetLevel, title, pageable);
        List<UUID> examIds = page.getContent().stream().map(Exam::getId).toList();
        Map<UUID, Long> counts = examIds.isEmpty() ? Map.of()
                : examRepo.countQuestionsForExams(examIds).stream()
                        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        UUID userId = userDirectory.requireCurrentUserId();
        Map<UUID, BigDecimal> bestScores = examIds.isEmpty() ? Map.of()
                : attemptRepo.findBestScoreByExam(userId, examIds).stream()
                        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
        Set<UUID> live = examIds.isEmpty() ? Set.of()
                : new HashSet<>(attemptRepo.findExamIdsWithLiveAttempt(userId, examIds));

        return page.map(exam -> LearnerExamListItemResult.of(exam, counts.getOrDefault(exam.getId(), 0L),
                bestScores.get(exam.getId()), live.contains(exam.getId())));
    }

    @Transactional(readOnly = true)
    public LearnerExamListItemResult detail(UUID examId) {
        Exam exam = requirePublishedExam(examId);
        UUID userId = userDirectory.requireCurrentUserId();
        List<UUID> ids = List.of(examId);
        BigDecimal best = attemptRepo.findBestScoreByExam(userId, ids).stream().map(row -> (BigDecimal) row[1])
                .findFirst().orElse(null);

        return LearnerExamListItemResult.of(exam, examRepo.countQuestions(examId), best,
                !attemptRepo.findExamIdsWithLiveAttempt(userId, ids).isEmpty());
    }

    @Transactional(readOnly = true)
    public LearnerExamListItemResult placementExam() {
        Exam exam = examRepo
                .findFirstByExamTypeAndStatusOrderByPublishedAtDesc(ExamType.PLACEMENT, ExamStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("PLACEMENT_EXAM_NOT_FOUND",
                        "No published placement exam is available"));
        return LearnerExamListItemResult.of(exam, examRepo.countQuestions(exam.getId()));
    }

    private Exam requirePublishedExam(UUID examId) {
        return examRepo.findById(examId).filter(exam -> exam.getStatus() == ExamStatus.PUBLISHED).orElseThrow(
                () -> new NotFoundException("EXAM_NOT_FOUND", "No published exam with id %s".formatted(examId)));
    }
}
