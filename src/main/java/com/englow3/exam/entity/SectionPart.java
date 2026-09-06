package com.englow3.exam.entity;

import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One numbered part of a section - Part 1 through Part 7 of a TOEIC L&R paper. See {@link ExamSection} for why there is
 * no association to its parent, no setter, and a {@code create(...)} factory instead. {@code metadata jsonb} is
 * deliberately not mapped: nothing reads it, and choosing how to map it (String, JsonNode, Map) is a decision for
 * whoever first needs its contents. An unmapped column is fine - {@code ddl-auto: validate} checks the columns an
 * entity claims, not that it claims every column.
 */
@Entity
@Table(name = "section_parts")
@Getter
public class SectionPart extends BasePersistedEntity {

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

    public static SectionPart create(UUID examSectionId, int orderNo, String title, String instruction, String content,
            String audioObjectKey, String imageObjectKey) {
        SectionPart part = new SectionPart();
        part.id = UUID.randomUUID();
        part.examSectionId = examSectionId;
        part.orderNo = orderNo;
        part.title = title;
        part.instruction = instruction;
        part.content = content;
        part.audioObjectKey = audioObjectKey;
        part.imageObjectKey = imageObjectKey;
        return part;
    }
}
