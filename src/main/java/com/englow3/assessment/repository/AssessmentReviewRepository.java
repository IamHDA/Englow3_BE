package com.englow3.assessment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.assessment.entity.AssessmentReview;

public interface AssessmentReviewRepository extends JpaRepository<AssessmentReview, UUID> {
    List<AssessmentReview> findByAttemptIdOrderByCreatedAtDesc(UUID attemptId);
}
