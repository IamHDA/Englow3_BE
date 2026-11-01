package com.englow3.roadmap.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.roadmap.entity.RoadmapStage;
import com.englow3.roadmap.entity.RoadmapStageStatus;

public interface RoadmapStageRepository extends JpaRepository<RoadmapStage, UUID> {

    List<RoadmapStage> findByRoadmapIdOrderByOrderNoAscCreatedAtAsc(UUID roadmapId);

    Optional<RoadmapStage> findFirstByRoadmapIdAndStatus(UUID roadmapId, RoadmapStageStatus status);
}
