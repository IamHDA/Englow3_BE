package com.englow3.roadmap.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.roadmap.entity.RoadmapAdjustment;

public interface RoadmapAdjustmentRepository extends JpaRepository<RoadmapAdjustment, UUID> {

    List<RoadmapAdjustment> findTop10ByRoadmapIdOrderByCreatedAtDesc(UUID roadmapId);
}
