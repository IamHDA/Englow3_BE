package com.englow3.learning.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.learning.entity.QuizAttempt;
import com.englow3.learning.entity.QuizAttemptStatus;

import jakarta.persistence.LockModeType;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findFirstByUserIdAndQuizIdAndStatusOrderByStartedAtDesc(UUID userId, UUID quizId,
            QuizAttemptStatus status);

    /** Locked for submission: two submits of one attempt must not both score it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from QuizAttempt a where a.id = :id")
    Optional<QuizAttempt> findByIdForUpdate(@Param("id") UUID id);
}
