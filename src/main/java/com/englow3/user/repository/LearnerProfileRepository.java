package com.englow3.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.user.entity.LearnerProfile;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, UUID> {

    Optional<LearnerProfile> findByUserId(UUID userId);
}
