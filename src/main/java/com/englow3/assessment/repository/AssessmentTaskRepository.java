package com.englow3.assessment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTask;
import com.englow3.assessment.entity.AssessmentTaskStatus;

import jakarta.persistence.LockModeType;

public interface AssessmentTaskRepository extends JpaRepository<AssessmentTask, UUID> {
    @Query("select t from AssessmentTask t where (:skill is null or t.skill=:skill) and (:status is null or t.status=:status) and (:author is null or t.createdByUserId=:author)")
    Page<AssessmentTask> search(@Param("skill") AssessmentSkill skill, @Param("status") AssessmentTaskStatus status,
            @Param("author") UUID author, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AssessmentTask t where t.id=:id")
    Optional<AssessmentTask> lockById(@Param("id") UUID id);
}
