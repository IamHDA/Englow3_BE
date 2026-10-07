package com.englow3.assessment.repository;

import java.util.*;
import com.englow3.assessment.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface AssessmentTaskRepository extends JpaRepository<AssessmentTask, UUID> {
    @Query("select t from AssessmentTask t where (:skill is null or t.skill=:skill) and (:status is null or t.status=:status) and (:author is null or t.createdByUserId=:author) order by t.createdAt desc")
    Page<AssessmentTask> search(@Param("skill") AssessmentSkill skill, @Param("status") AssessmentTaskStatus status,
            @Param("author") UUID author, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AssessmentTask t where t.id=:id")
    Optional<AssessmentTask> lockById(@Param("id") UUID id);
}
