package com.englow3.support;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A paper, with the five levels of tree under it.
 * <p>
 * Built in SQL for the same reason as {@link LearnerFixture}: the queries under test assemble this tree themselves from
 * five separate reads, and building the fixture through the same mappings would hide a mismatch between what is stored
 * and what is read back.
 */
public final class ExamFixture {

    private final JdbcClient jdbc;

    public ExamFixture(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID publishedExam(String title, UUID authorId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into exams (id, title, description, exam_type, target_level, duration_seconds,
                                   max_raw_score, pass_score, status, version_number, created_by_user_id)
                values (:id, :title, '', 'MOCK', 'B1', 3600, 200, 100, 'PUBLISHED', 1, :authorId)
                """).param("id", id).param("title", title).param("authorId", authorId).update();

        return id;
    }

    public UUID section(UUID examId, int orderNo, String sectionType) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into exam_sections (id, exam_id, section_type, order_no, max_raw_score, is_scored_by_criteria)
                values (:id, :examId, :sectionType, :orderNo, 100, false)
                """).param("id", id).param("examId", examId).param("sectionType", sectionType).param("orderNo", orderNo)
                .update();

        return id;
    }

    public UUID part(UUID sectionId, int orderNo, String title) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into section_parts (id, exam_section_id, order_no, title)
                values (:id, :sectionId, :orderNo, :title)
                """).param("id", id).param("sectionId", sectionId).param("orderNo", orderNo).param("title", title)
                .update();

        return id;
    }

    public UUID questionSet(UUID partId, int orderNo) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into question_sets (id, section_part_id, order_no)
                values (:id, :partId, :orderNo)
                """).param("id", id).param("partId", partId).param("orderNo", orderNo).update();

        return id;
    }

    public UUID question(UUID setId, int orderNo, String content) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into questions (id, question_set_id, question_type, content, difficulty_level, skill_type,
                                       order_no, max_raw_score, explanation)
                values (:id, :setId, 'SINGLE_CHOICE', :content, 'MEDIUM', 'READING', :orderNo, 1,
                        'Because of the rule.')
                """).param("id", id).param("setId", setId).param("orderNo", orderNo).param("content", content).update();

        return id;
    }

    /** {@code correct} is the field that must never reach a learner's copy of the paper. */
    public UUID option(UUID questionId, int orderNo, String content, boolean correct) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into question_options (id, question_id, content, order_no, is_correct, explanation)
                values (:id, :questionId, :content, :orderNo, :correct, 'Why this option is what it is.')
                """).param("id", id).param("questionId", questionId).param("content", content).param("orderNo", orderNo)
                .param("correct", correct).update();

        return id;
    }
}
