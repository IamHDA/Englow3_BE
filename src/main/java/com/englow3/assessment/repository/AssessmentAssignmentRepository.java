package com.englow3.assessment.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.englow3.assessment.entity.AssessmentAssignment;

public interface AssessmentAssignmentRepository extends JpaRepository<AssessmentAssignment, UUID> {
    List<AssessmentAssignment> findTop20ByAttemptIdOrderByCreatedAtDesc(UUID attemptId);
}
