package com.englow3.roadmap.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.roadmap.entity.RoadmapItem;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, UUID> {

    List<RoadmapItem> findByStageIdInOrderByStageIdAscOrderNoAsc(Collection<UUID> stageIds);

    List<RoadmapItem> findByStageIdOrderByOrderNoAsc(UUID stageId);
}
