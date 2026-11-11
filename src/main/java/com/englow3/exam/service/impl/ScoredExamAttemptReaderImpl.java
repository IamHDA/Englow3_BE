package com.englow3.exam.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.ExamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScoredExamAttemptReaderImpl implements ScoredExamAttemptReader {
    private final ExamAttemptRepository attemptRepo;
    private final ExamRepository examRepo;
    private final JdbcClient jdbcClient;

    @Override
    @Transactional(readOnly = true)
    public Optional<Sitting> read(UUID attemptId, UUID ownerId) {
        return attemptRepo.findById(attemptId).filter(attempt -> attempt.getUserId().equals(ownerId))
                .flatMap(attempt -> examRepo.findById(attempt.getExamId()).map(exam -> sitting(attempt, exam)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sitting> latestFullSitting(UUID examId, UUID ownerId, Instant since) {
        return attemptRepo
                .findFirstByUserIdAndExamIdAndModeAndStatusAndStartedAtGreaterThanEqualOrderByStartedAtDesc(ownerId,
                        examId, ExamAttemptMode.FULL, ExamAttemptStatus.SCORED, since)
                .flatMap(attempt -> examRepo.findById(examId).map(exam -> sitting(attempt, exam)));
    }

    private Sitting sitting(ExamAttempt attempt, Exam exam) {
        boolean scored = attempt.getStatus() == ExamAttemptStatus.SCORED;
        return new Sitting(attempt.getId(), exam.getId(), exam.getExamType().name(),
                exam.getCertificateType() == null ? null : exam.getCertificateType().name(),
                exam.getCertificateVariant() == null ? null : exam.getCertificateVariant().name(), scored,
                attempt.getMode() == ExamAttemptMode.FULL, scored ? attempt.getScorePercentage() : null,
                scored ? attempt.getConvertedScore() : null, attempt.getScoredAt(),
                scored ? sections(attempt.getId(), exam.getId()) : List.of(), attempt.getStartedAt());
    }

    /**
     * Points per section over the questions the sitting covered: the parts a practice chose, or the whole paper. A
     * question left unanswered has no answer row and so adds to the maximum and not to the score.
     */
    private List<SectionScore> sections(UUID attemptId, UUID examId) {
        return jdbcClient.sql("""
                select es.section_type,
                       coalesce(sum(aa.awarded_raw_score), 0) as awarded,
                       coalesce(sum(q.max_raw_score), 0) as max
                  from exam_sections es
                  join section_parts sp on sp.exam_section_id = es.id
                  join question_sets qs on qs.section_part_id = sp.id
                  join questions q on q.question_set_id = qs.id
                  left join attempt_answers aa on aa.question_id = q.id and aa.exam_attempt_id = :attemptId
                 where es.exam_id = :examId
                   and (not exists (select 1 from exam_attempt_parts p where p.exam_attempt_id = :attemptId)
                        or sp.id in (select p.section_part_id from exam_attempt_parts p
                                      where p.exam_attempt_id = :attemptId))
                 group by es.section_type, es.order_no
                 order by es.order_no
                """).param("attemptId", attemptId).param("examId", examId)
                .query((rs, row) -> new SectionScore(rs.getString("section_type"), rs.getBigDecimal("awarded"),
                        rs.getBigDecimal("max")))
                .list();
    }
}
