package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.exam.entity.AttemptSectionResult;

public interface AttemptSectionResultRepository extends JpaRepository<AttemptSectionResult, UUID> {

    List<AttemptSectionResult> findByExamAttemptId(UUID examAttemptId);
}
