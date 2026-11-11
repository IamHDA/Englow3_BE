package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;

public interface FullTestRepository extends JpaRepository<FullTest, UUID> {
    @Query("select f from FullTest f where f.createdByUserId=:author or f.status=com.englow3.exam.entity.ExamStatus.PUBLISHED order by f.createdAt desc")
    List<FullTest> visibleForAuthoring(@Param("author") UUID author);

    @Query(value = """
            select f.* from full_tests f join exams e on e.id=f.exam_id
            join assessment_tasks w1 on w1.id=f.writing_task1_id
            join assessment_tasks w2 on w2.id=f.writing_task2_id
            join assessment_tasks s1 on s1.id=f.speaking_part1_id
            join assessment_tasks s2 on s2.id=f.speaking_part2_id
            join assessment_tasks s3 on s3.id=f.speaking_part3_id
            where f.status='PUBLISHED' and f.certificate_variant='ACADEMIC' and e.status='PUBLISHED' and e.exam_type='MOCK'
              and e.certificate_type='IELTS' and e.certificate_variant=f.certificate_variant
              and w1.status='PUBLISHED' and w1.skill='WRITING' and w1.task_type='TASK_1' and w1.certificate_type='IELTS'
              and w2.status='PUBLISHED' and w2.skill='WRITING' and w2.task_type='TASK_2' and w2.certificate_type='IELTS'
              and s1.status='PUBLISHED' and s1.skill='SPEAKING' and s1.task_type='PART_1' and s1.certificate_type='IELTS'
              and s2.status='PUBLISHED' and s2.skill='SPEAKING' and s2.task_type='PART_2' and s2.certificate_type='IELTS'
              and s3.status='PUBLISHED' and s3.skill='SPEAKING' and s3.task_type='PART_3' and s3.certificate_type='IELTS'
              and (f.certificate_variant<>'ACADEMIC' or w1.image_object_key is not null)
              and exists(select 1 from exam_sections x where x.exam_id=e.id and x.section_type='LISTENING' and x.max_raw_score=40)
              and exists(select 1 from exam_sections x where x.exam_id=e.id and x.section_type='READING' and x.max_raw_score=40)
            order by f.published_at desc
            """, nativeQuery = true)
    List<FullTest> availableCatalogue();

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
