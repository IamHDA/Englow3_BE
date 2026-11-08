package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;

public interface FullTestRepository extends JpaRepository<FullTest, UUID> {

    /** Serializes component archival with publication and starting a sitting. */
    @Query(value = "select cast(pg_advisory_xact_lock(8723149201) as text)", nativeQuery = true)
    String lockAvailability();

    @Query(value = """
            select exists(select 1 from full_tests f where f.exam_id = :id
             and (f.status in ('PUBLISHED', 'PENDING_REVIEW') or exists
              (select 1 from full_test_sittings s where s.full_test_id = f.id
               and s.status in ('IN_PROGRESS', 'AWAITING_GRADES'))))
            """, nativeQuery = true)
    boolean protectsPaper(@Param("id") UUID id);

    @Query(value = """
            select exists(select 1 from full_tests f where :id in
             (f.writing_task1_id, f.writing_task2_id, f.speaking_part1_id, f.speaking_part2_id, f.speaking_part3_id)
             and (f.status in ('PUBLISHED', 'PENDING_REVIEW') or exists
              (select 1 from full_test_sittings s where s.full_test_id = f.id
               and s.status in ('IN_PROGRESS', 'AWAITING_GRADES'))))
            """, nativeQuery = true)
    boolean protectsTask(@Param("id") UUID id);

    List<FullTest> findByStatusOrderByPublishedAtDesc(ExamStatus status);

    List<FullTest> findAllByOrderByCreatedAtDesc();
}
