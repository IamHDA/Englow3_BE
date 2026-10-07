package com.englow3.assessment.repository;

import java.util.*;
import com.englow3.assessment.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, UUID> {
    @Query("select a from AssessmentAttempt a left join AssessmentResultRead r on r.attemptId=a.id where a.userId=:user and a.status=com.englow3.assessment.entity.AssessmentAttemptStatus.COMPLETED and (r.attemptId is null or r.resultVersion<>a.version) order by a.assessedAt desc,a.id")
    Page<AssessmentAttempt> unreadResults(@Param("user") UUID user, Pageable pageable);

    @Query("select a from AssessmentAttempt a, AssessmentTask t where a.taskId=t.id and a.userId=:user and (:task is null or a.taskId=:task) and (:skill is null or a.skill=:skill) and (:status is null or a.status=:status) and (:title is null or lower(t.title) like lower(concat('%',cast(:title as String),'%'))) order by a.createdAt desc,a.id")
    Page<AssessmentAttempt> filteredHistory(@Param("user") UUID user, @Param("task") UUID task,
            @Param("skill") AssessmentSkill skill, @Param("status") AssessmentAttemptStatus status,
            @Param("title") String title, Pageable pageable);

    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:key,0)) as text)", nativeQuery = true)
    String lockRequest(@Param("key") String key);

    Optional<AssessmentAttempt> findByUserIdAndClientKey(UUID user, UUID key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssessmentAttempt a where a.id=:id")
    Optional<AssessmentAttempt> lockById(@Param("id") UUID id);

    @Query("select a from AssessmentAttempt a where a.userId=:user and (:task is null or a.taskId=:task) order by a.createdAt desc")
    Page<AssessmentAttempt> history(@Param("user") UUID user, @Param("task") UUID task, Pageable pageable);

    @Query("select a from AssessmentAttempt a, AssessmentTask t where a.taskId=t.id and (:author is null or t.createdByUserId=:author) and (:status is null or a.status=:status) and a.status<>com.englow3.assessment.entity.AssessmentAttemptStatus.DRAFT order by a.submittedAt desc")
    Page<AssessmentAttempt> reviewQueue(@Param("author") UUID author, @Param("status") AssessmentAttemptStatus status,
            Pageable pageable);
}
