package com.englow3.learning.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "dictation_lessons")
@Getter
public class DictationLesson {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String topic;

    @Column(name = "target_level")
    private String targetLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DictationLessonStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected DictationLesson() {
    }

    public static DictationLesson draft(String slug, String title, String topic, String targetLevel,
            UUID createdByUserId) {
        DictationLesson lesson = new DictationLesson();
        lesson.id = UUID.randomUUID();
        lesson.slug = slug;
        lesson.title = title;
        lesson.topic = topic;
        lesson.targetLevel = targetLevel;
        lesson.status = DictationLessonStatus.DRAFT;
        lesson.createdByUserId = createdByUserId;
        return lesson;
    }

    public void publish(long sentenceCount, Instant now) {
        if (status != DictationLessonStatus.DRAFT) {
            throw new ConflictException("DICTATION_LESSON_NOT_DRAFT",
                    "Only a draft lesson can be published; this one is %s".formatted(status));
        }
        if (sentenceCount == 0) {
            throw new ConflictException("DICTATION_LESSON_EMPTY", "A lesson with no sentences cannot be published");
        }
        this.status = DictationLessonStatus.PUBLISHED;
        this.publishedAt = now;
    }

    public void archive() {
        if (status == DictationLessonStatus.ARCHIVED) {
            throw new ConflictException("DICTATION_LESSON_ALREADY_ARCHIVED", "This lesson is already archived");
        }
        this.status = DictationLessonStatus.ARCHIVED;
    }
}
