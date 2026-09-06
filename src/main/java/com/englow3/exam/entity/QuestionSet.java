package com.englow3.exam.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The group of questions that share one stimulus: a TOEIC Part 3 conversation, or a Part 6/7 passage. {@code content}
 * is that passage text and {@code audioObjectKey} that recording - which is why this level exists at all rather than
 * hanging every question straight off the part. Read-only, see {@link ExamSection}.
 *
 * {@code metadata jsonb} is deliberately not mapped: nothing reads it, and choosing how to map it (String, JsonNode,
 * Map) is a decision for whoever first needs its contents. {@code is_single_use} was dropped from the table entirely
 * - it implied a question-bank / reuse concept nothing here decided, so there was no meaning to guard.
 */
@Entity
@Table(name = "question_sets")
@Getter
public class QuestionSet {

    @Id
    private UUID id;

    @Column(name = "section_part_id", nullable = false)
    private UUID sectionPartId;

    private String title;

    private String instruction;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    /** The passage a group of questions reads from - added by V029 for TOEIC Part 6/7. */
    private String content;

    @Column(name = "audio_object_key")
    private String audioObjectKey;

    @Column(name = "image_object_key")
    private String imageObjectKey;

    protected QuestionSet() {
    }
}
