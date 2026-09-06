package com.englow3.exam.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.ExamSection;

/**
 * One of the five content repositories authoring writes through. They exist now because each level has its own write
 * path - before authoring shipped, these entities were only ever reached through the paper tree, which is why the
 * module had no repository for them.
 */
public interface ExamSectionRepository extends JpaRepository<ExamSection, UUID> {

    /**
     * Bulk, one statement - not {@code deleteAll(findAll(...))}, which would load every row to delete it one by one.
     * The caller deletes bottom-up: every foreign key below {@code exams} is {@code on delete restrict}, so this level
     * must go last.
     */
    @Modifying
    @Query("delete from ExamSection s where s.examId = :examId")
    void deleteAllForExam(@Param("examId") UUID examId);
}
