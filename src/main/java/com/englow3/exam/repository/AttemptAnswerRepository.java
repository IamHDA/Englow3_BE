package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.exam.entity.AttemptAnswer;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, UUID> {
    List<AttemptAnswer> findByExamAttemptId(UUID examAttemptId);
}
