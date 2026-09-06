package com.englow3.exam.entity;

import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The group of questions that share one stimulus: a TOEIC Part 3 conversation, or a Part 6/7 passage. {@code content}
 * is that passage text and {@code audioObjectKey} that recording - which is why this level exists at all rather than
 * hanging every question straight off the part. See {@link ExamSection} for why there is no association to its parent,
 * no setter, and a {@code create(...)} factory instead. {@code metadata jsonb} stays unmapped: nothing reads it yet,
 * and how to map {@code jsonb} is a decision for whoever first needs its contents. {@code is_single_use} is gone from
 * the table entirely - it implied a question-bank concept nothing had decided yet; the bank that Phase 5 actually built
 * is {@code sourceQuestionSetId}, set only when this row is a copy made from the bank, naming the original.
 */
@Entity
@Table(name = "question_sets")
@Getter
public class QuestionSet extends BasePersistedEntity {

    @Column(name = "section_part_id", nullable = false)
    private UUID sectionPartId;

    private String title;

    private String instruction;

    @Column(name = "order_no", nullable = false)
    private int orderNo;

    /** The passage a group of questions reads from. */
    private String content;

    @Column(name = "audio_object_key")
    private String audioObjectKey;

    @Column(name = "image_object_key")
    private String imageObjectKey;

    @Column(name = "source_question_set_id")
    private UUID sourceQuestionSetId;

    protected QuestionSet() {
    }

    public static QuestionSet create(UUID sectionPartId, String title, String instruction, int orderNo, String content,
            String audioObjectKey, String imageObjectKey, UUID sourceQuestionSetId) {
        QuestionSet questionSet = new QuestionSet();
        questionSet.id = UUID.randomUUID();
        questionSet.sectionPartId = sectionPartId;
        questionSet.title = title;
        questionSet.instruction = instruction;
        questionSet.orderNo = orderNo;
        questionSet.content = content;
        questionSet.audioObjectKey = audioObjectKey;
        questionSet.imageObjectKey = imageObjectKey;
        questionSet.sourceQuestionSetId = sourceQuestionSetId;
        return questionSet;
    }
}
