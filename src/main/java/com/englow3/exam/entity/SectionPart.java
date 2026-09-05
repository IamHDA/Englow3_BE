package com.englow3.exam.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One numbered part of a section - Part 1 through Part 7 of a TOEIC L&R paper. Read-only, see {@link ExamSection}.
 * {@code metadata jsonb} is deliberately not mapped: nothing reads it, and choosing how to map it (String, JsonNode,
 * Map) is a decision for whoever first needs its contents. An unmapped column is fine - {@code ddl-auto: validate}
 * checks the columns an entity claims, not that it claims every column.
 */
@Entity
@Table(name = "section_parts")
@Getter
public class SectionPart {

    @Id
    private UUID id;

    @Column(name = "exam_section_id", nullable = false)
    private UUID examSectionId;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    @Column(nullable = false)
    private String title;

    private String instruction;

    private String content;

    @Column(name = "audio_object_key")
    private String audioObjectKey;

    @Column(name = "image_object_key")
    private String imageObjectKey;

    protected SectionPart() {
    }
}
