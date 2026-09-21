package com.englow3.learning.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.learning.entity.QuizAttemptAnswer;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, UUID> {

    List<QuizAttemptAnswer> findByQuizAttemptId(UUID quizAttemptId);
}
