package com.englow3.exam.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /**
     * Entities rather than a projection: the row has no collection to lazy-load and one page is a handful of them, so
     * the projection would only buy a hand-written count query. ponytail: three null-guard filters inline. Move to
     * exam/query/ with composed conditions when the catalogue holds more than one certificate and filtering by type /
     * variant / level starts to mean something.
     */
    @Query("""
            select e from Exam e
            where (:status is null or e.status = :status)
              and (:examType is null or e.examType = :examType)
              and (:title is null or lower(e.title) like lower(concat('%', :title, '%')))
            """)
    Page<Exam> search(@Param("status") ExamStatus status, @Param("examType") ExamType examType,
            @Param("title") String title, Pageable pageable);
}
