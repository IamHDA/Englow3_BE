package com.englow3.exam.repository;

import java.util.Optional;
import java.util.UUID;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id")
    Optional<ExamAttempt> findByIdForUpdate(@Param("id") UUID id);
}
