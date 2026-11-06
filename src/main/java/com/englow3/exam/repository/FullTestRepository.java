package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;

public interface FullTestRepository extends JpaRepository<FullTest, UUID> {

    List<FullTest> findByStatusOrderByPublishedAtDesc(ExamStatus status);

    List<FullTest> findAllByOrderByCreatedAtDesc();
}
