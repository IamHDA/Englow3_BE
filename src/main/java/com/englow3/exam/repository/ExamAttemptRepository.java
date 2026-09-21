package com.englow3.exam.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.ExamAttempt;
import com.englow3.exam.entity.ExamAttemptStatus;

import jakarta.persistence.LockModeType;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    Optional<ExamAttempt> findFirstByUserIdAndExamIdAndStatusOrderByStartedAtDesc(UUID userId, UUID examId,
            ExamAttemptStatus status);

    /**
     * The learner's best score on each of these papers, and how many times they have sat it. Batched so a page of exams
     * costs one query rather than two per row.
     */
    @Query("""
            select a.examId, max(a.scorePercentage), count(a)
              from ExamAttempt a
             where a.userId = :userId and a.examId in :examIds
               and a.status = com.englow3.exam.entity.ExamAttemptStatus.SCORED
             group by a.examId
            """)
    List<Object[]> findBestScoreByExam(@Param("userId") UUID userId, @Param("examIds") Collection<UUID> examIds);

    /** Whether a paper is currently open for this learner - what the catalogue card calls "in progress". */
    @Query("""
            select a.examId from ExamAttempt a
             where a.userId = :userId and a.examId in :examIds
               and a.status = com.englow3.exam.entity.ExamAttemptStatus.IN_PROGRESS
            """)
    List<UUID> findExamIdsWithLiveAttempt(@Param("userId") UUID userId, @Param("examIds") Collection<UUID> examIds);

    /** The learner's own attempt history, newest first. */
    Page<ExamAttempt> findByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id")
    Optional<ExamAttempt> findByIdForUpdate(@Param("id") UUID id);
}
