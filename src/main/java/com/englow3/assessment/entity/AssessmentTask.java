package com.englow3.assessment.entity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Table(name = "assessment_tasks")
@Getter
public class AssessmentTask {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private AssessmentSkill skill;
    private String title;
    private String taskType;
    @Column(columnDefinition = "text")
    private String instructions;
    @Column(columnDefinition = "text")
    private String rubricNotes;
    @Column(columnDefinition = "text")
    private String sampleAnswer;
    /** Writing Task 1 only: the chart the learner describes, as a key in the learning bucket. */
    private String imageObjectKey;
    private int minimumWords;
    private int timeLimitSeconds;
    /** CEFR band the task is pitched at; null for a task meant for any level. */
    private String targetLevel;
    @Enumerated(EnumType.STRING)
    private AssessmentTaskPurpose purpose;
    /** IELTS or TOEIC when the task follows that exam's format; null for general English. */
    private String certificateType;
    @Enumerated(EnumType.STRING)
    private AssessmentTaskStatus status;
    private UUID createdByUserId;
    @Column(columnDefinition = "text")
    private String reviewNote;
    private UUID reviewedByUserId;
    private Instant publishedAt;
    @Version
    private long version;
    @org.hibernate.annotations.CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private static final Set<String> CEFR_LEVELS = Set.of("A1", "A2", "B1", "B2", "C1", "C2");
    private static final Set<String> CERTIFICATES = Set.of("IELTS", "TOEIC");

    protected AssessmentTask() {
    }

    public static AssessmentTask draft(AssessmentTaskCommand command, UUID author) {
        AssessmentTask task = new AssessmentTask();
        task.id = UUID.randomUUID();
        task.createdByUserId = author;
        task.status = AssessmentTaskStatus.DRAFT;
        task.apply(command);
        return task;
    }

    public void edit(AssessmentTaskCommand command) {
        if (status != AssessmentTaskStatus.DRAFT && status != AssessmentTaskStatus.REJECTED) {
            throw new ConflictException("TASK_NOT_EDITABLE", "Only draft or rejected tasks can be edited");
        }
        if (command.skill() != skill) {
            throw new BadRequestException("TASK_SKILL_IMMUTABLE", "Create a new task to change its skill");
        }
        apply(command);
    }

    private void apply(AssessmentTaskCommand c) {
        if (c.skill() == null || c.title() == null || c.title().isBlank() || c.instructions() == null
                || c.instructions().isBlank()) {
            throw new BadRequestException("TASK_CONTENT_REQUIRED", "Task title and instructions are required");
        }
        boolean valid = c.skill() == AssessmentSkill.WRITING ? Set.of("TASK_1", "TASK_2").contains(c.taskType())
                : Set.of("PART_1", "PART_2", "PART_3").contains(c.taskType());
        if (!valid) {
            throw new BadRequestException("TASK_TYPE_INVALID", "Task type does not match the skill");
        }
        if (c.minimumWords() < 0 || c.minimumWords() > 1000 || c.timeLimitSeconds() < 30
                || c.timeLimitSeconds() > 3600) {
            throw new BadRequestException("TASK_LIMIT_INVALID", "Task limits are invalid");
        }
        if (c.targetLevel() != null && !CEFR_LEVELS.contains(c.targetLevel())) {
            throw new BadRequestException("TASK_LEVEL_INVALID", "Target level must be a CEFR band");
        }
        if (c.certificateType() != null && !CERTIFICATES.contains(c.certificateType())) {
            throw new BadRequestException("TASK_CERTIFICATE_INVALID", "Certificate must be IELTS or TOEIC");
        }
        AssessmentTaskPurpose nextPurpose = c.purpose() == null ? AssessmentTaskPurpose.PRACTICE : c.purpose();
        // A level check measures speaking: it is the spoken part of the roadmap's level test, whose other parts are an
        // exam the exam module grades.
        if (nextPurpose == AssessmentTaskPurpose.LEVEL_CHECK && c.skill() != AssessmentSkill.SPEAKING) {
            throw new BadRequestException("TASK_PURPOSE_SKILL", "A level-check task must be a speaking task");
        }
        skill = c.skill();
        title = c.title().strip();
        taskType = c.taskType();
        instructions = c.instructions().strip();
        rubricNotes = c.rubricNotes() == null ? "" : c.rubricNotes().strip();
        sampleAnswer = c.sampleAnswer() == null ? "" : c.sampleAnswer().strip();
        minimumWords = c.minimumWords();
        timeLimitSeconds = c.timeLimitSeconds();
        targetLevel = c.targetLevel();
        purpose = nextPurpose;
        certificateType = c.certificateType();
    }

    /**
     * The chart a Writing Task 1 describes. Only that task has one - a Task 2 essay or a spoken part describing a
     * picture would be a different exercise - and only while the task can still be edited, since a published task is
     * what attempts were snapshotted from.
     */
    public void attachImage(String objectKey) {
        requireImageable();
        this.imageObjectKey = objectKey;
    }

    public void removeImage() {
        requireImageable();
        this.imageObjectKey = null;
    }

    /** Whether a chart may be attached now; changes nothing. */
    public void requireImageable() {
        if (status != AssessmentTaskStatus.DRAFT && status != AssessmentTaskStatus.REJECTED) {
            throw new ConflictException("TASK_NOT_EDITABLE", "Only draft or rejected tasks can be edited");
        }
        if (skill != AssessmentSkill.WRITING || !"TASK_1".equals(taskType)) {
            throw new BadRequestException("TASK_IMAGE_NOT_ALLOWED", "Only a Writing Task 1 has a chart");
        }
    }

    public void submit() {
        if (status != AssessmentTaskStatus.DRAFT && status != AssessmentTaskStatus.REJECTED) {
            throw new ConflictException("TASK_NOT_SUBMITTABLE", "This task cannot be submitted");
        }
        status = AssessmentTaskStatus.PENDING_REVIEW;
    }

    public void approve(UUID reviewer, Instant now) {
        requirePending();
        status = AssessmentTaskStatus.PUBLISHED;
        reviewedByUserId = reviewer;
        publishedAt = now;
        reviewNote = null;
    }

    public void reject(UUID reviewer, String note) {
        requirePending();
        if (note == null || note.isBlank()) {
            throw new BadRequestException("REVIEW_NOTE_REQUIRED", "Explain the requested changes");
        }
        status = AssessmentTaskStatus.REJECTED;
        reviewedByUserId = reviewer;
        reviewNote = note.strip();
    }

    /**
     * Undoes an archive. Back to where it was: a task that had been published returns to the library as it was -
     * published content is never edited, so it needs no second review - and one that never was goes back to draft.
     * Archiving used to be one-way; a mistaken click could only be undone in the database.
     */
    public void restore() {
        if (status != AssessmentTaskStatus.ARCHIVED) {
            throw new ConflictException("TASK_NOT_ARCHIVED", "Only an archived task can be restored");
        }
        this.status = publishedAt != null ? AssessmentTaskStatus.PUBLISHED : AssessmentTaskStatus.DRAFT;
    }

    public void archive() {
        if (status != AssessmentTaskStatus.PUBLISHED) {
            throw new ConflictException("TASK_NOT_PUBLISHED", "Only published tasks can be archived");
        }
        status = AssessmentTaskStatus.ARCHIVED;
    }

    private void requirePending() {
        if (status != AssessmentTaskStatus.PENDING_REVIEW) {
            throw new ConflictException("TASK_NOT_PENDING", "Task is not awaiting review");
        }
    }
}
