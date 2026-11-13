package com.englow3.roadmap.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.RoadmapStatus;

import jakarta.persistence.LockModeType;

public interface LearningRoadmapRepository extends JpaRepository<LearningRoadmap, UUID> {

    /** The plan in play, if any: at most one, by a partial unique index. */
    @Query("""
            select r from LearningRoadmap r
             where r.userId = :userId
               and r.status in (com.englow3.roadmap.entity.RoadmapStatus.GENERATING,
                                com.englow3.roadmap.entity.RoadmapStatus.ACTIVE)
            """)
    Optional<LearningRoadmap> findOpen(@Param("userId") UUID userId);

    /** The newest plan whatever its state, so a failed or finished one can still be shown. */
    Optional<LearningRoadmap> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<LearningRoadmap> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId,
            List<RoadmapStatus> statuses);

    /** Active roadmaps, oldest first, for the periodic sweep. */
    @Query("""
            select r.id from LearningRoadmap r
             where r.status = com.englow3.roadmap.entity.RoadmapStatus.ACTIVE
             order by r.createdAt
            """)
    List<UUID> findActiveIds(org.springframework.data.domain.Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LearningRoadmap r where r.id = :id")
    Optional<LearningRoadmap> findByIdForUpdate(@Param("id") UUID id);
}
