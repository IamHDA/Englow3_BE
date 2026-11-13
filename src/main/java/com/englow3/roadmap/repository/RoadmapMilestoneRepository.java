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

    List<RoadmapMilestone> findByStageIdIn(Collection<UUID> stageIds);

    Optional<RoadmapMilestone> findByStageId(UUID stageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from RoadmapMilestone m where m.id = :id")
    Optional<RoadmapMilestone> findByIdForUpdate(@Param("id") UUID id);
}
