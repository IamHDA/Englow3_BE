package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.exam.entity.AttemptAnswerOption;

public interface AttemptAnswerOptionRepository extends JpaRepository<AttemptAnswerOption, UUID> {
    List<AttemptAnswerOption> findByAttemptAnswerIdIn(List<UUID> answerIds);
}
