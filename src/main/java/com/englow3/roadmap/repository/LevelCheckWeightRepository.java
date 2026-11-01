package com.englow3.roadmap.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.roadmap.entity.LevelCheckWeight;

public interface LevelCheckWeightRepository extends JpaRepository<LevelCheckWeight, UUID> {

    /** The latest changes, newest first. */
    List<LevelCheckWeight> findTop30ByOrderByEffectiveFromDesc();
}
