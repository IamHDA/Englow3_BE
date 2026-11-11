package com.englow3.roadmap.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.roadmap.entity.RoadmapMilestone;

import jakarta.persistence.LockModeType;

public interface RoadmapMilestoneRepository extends JpaRepository<RoadmapMilestone, UUID> {
    @Query("select count(m)>0 from RoadmapMilestone m, RoadmapStage s where m.stageId=s.id and s.roadmapId=:roadmap and m.status=com.englow3.roadmap.entity.MilestoneStatus.PASSED and (m.resultExamAttemptId=:sitting or m.resultFullTestSittingId=:sitting)")
    boolean wasUsedInPassedMilestone(@Param("roadmap") UUID roadmap, @Param("sitting") UUID sitting);

    List<RoadmapMilestone> findByStageIdIn(Collection<UUID> stageIds);

    Optional<RoadmapMilestone> findByStageId(UUID stageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from RoadmapMilestone m where m.id = :id")
    Optional<RoadmapMilestone> findByIdForUpdate(@Param("id") UUID id);
}
