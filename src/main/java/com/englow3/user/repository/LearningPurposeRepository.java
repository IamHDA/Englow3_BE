package com.englow3.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.user.entity.LearningPurpose;

public interface LearningPurposeRepository extends JpaRepository<LearningPurpose, Integer> {

    Optional<LearningPurpose> findByPurposeCode(String purposeCode);
}
