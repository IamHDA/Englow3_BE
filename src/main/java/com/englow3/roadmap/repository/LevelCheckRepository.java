package com.englow3.roadmap.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.roadmap.entity.LevelCheck;
import com.englow3.roadmap.entity.LevelCheckStatus;

import jakarta.persistence.LockModeType;

public interface LevelCheckRepository extends JpaRepository<LevelCheck, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LevelCheck c where c.id = :id")
    Optional<LevelCheck> findByIdForUpdate(@Param("id") UUID id);

    /** An unfinished check for the same purpose and milestone, picked up again rather than started twice. */
    Optional<LevelCheck> findFirstByUserIdAndMilestoneIdAndStatusNotOrderByCreatedAtDesc(UUID userId, UUID milestoneId,
            LevelCheckStatus status);

    @Query("""
            select c from LevelCheck c
             where c.userId = :userId and c.milestoneId is null
               and c.status <> com.englow3.roadmap.entity.LevelCheckStatus.COMPLETED
             order by c.createdAt desc
             limit 1
            """)
    Optional<LevelCheck> findOpenOnboarding(@Param("userId") UUID userId);

    Optional<LevelCheck> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** The learner's unfinished checks on this paper - the ones a new sitting of it may belong to. */
    List<LevelCheck> findByUserIdAndExamIdAndStatusNot(UUID userId, UUID examId, LevelCheckStatus status);

    /** The learner's unfinished checks asking for this spoken task. */
    List<LevelCheck> findByUserIdAndSpeakingTaskIdAndStatusNot(UUID userId, UUID taskId, LevelCheckStatus status);

    /** Unfinished checks started since {@code since}, oldest first, for the periodic sweep. */
    @Query("""
            select c.id from LevelCheck c
             where c.status <> com.englow3.roadmap.entity.LevelCheckStatus.COMPLETED and c.createdAt >= :since
             order by c.createdAt
            """)
    List<UUID> findUnfinishedSince(@Param("since") java.time.Instant since,
            org.springframework.data.domain.Pageable page);

    /** Papers and tasks the learner has already met, so a retake gets fresh ones where there are any. */
    @Query("select c.examId from LevelCheck c where c.userId = :userId")
    List<UUID> usedExamIds(@Param("userId") UUID userId);

    @Query("select c.speakingTaskId from LevelCheck c where c.userId = :userId and c.speakingTaskId is not null")
    List<UUID> usedSpeakingTaskIds(@Param("userId") UUID userId);
}
