package com.englow3.assessment.repository;

import com.englow3.assessment.entity.AssessmentResultRead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AssessmentResultReadRepository extends JpaRepository<AssessmentResultRead, UUID> {
}
