package com.englow3.exam.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.SectionPart;

/** See {@link ExamSectionRepository} for why the five content repositories exist. */
public interface SectionPartRepository extends JpaRepository<SectionPart, UUID> {

    /**
     * Descends to the paper by id rather than by a column of its own - {@code section_parts} carries no
     * {@code exam_id}, and the content entities hold plain UUID keys with no association to navigate.
     */
    @Modifying
    @Query("delete from SectionPart p where p.examSectionId in (select s.id from ExamSection s where s.examId = :examId)")
    void deleteAllForExam(@Param("examId") UUID examId);
}
