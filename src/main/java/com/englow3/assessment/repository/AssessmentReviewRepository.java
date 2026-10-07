package com.englow3.assessment.repository;

import java.util.UUID;
import com.englow3.assessment.entity.AssessmentReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentReviewRepository extends JpaRepository<AssessmentReview, UUID> {
    java.util.List<AssessmentReview> findByAttemptIdOrderByCreatedAtDesc(UUID attemptId);
}
