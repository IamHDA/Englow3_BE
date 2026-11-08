package com.englow3.exam.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * An IELTS full test: one Listening + Reading paper and five Writing and Speaking tasks, sat as one test with one
 * overall band (docs/decisions.md, D-IELTS-10). It follows the paper's review workflow - DRAFT to PENDING_REVIEW to
 * PUBLISHED, REJECTED as the way back - and, like a paper, can only go out when learners can actually sit it: every
 * component published and of the right IELTS kind. The tasks belong to the assessment module, so their facts arrive as
 * plain values the service read for it.
 */
@Entity
@Table(name = "full_tests")
@Getter
public class FullTest extends BasePersistedEntity {

    /** Which task each of the five Writing and Speaking slots must hold. */
    public enum Slot {
        WRITING_TASK_1("WRITING", "TASK_1"), WRITING_TASK_2("WRITING", "TASK_2"), SPEAKING_PART_1("SPEAKING", "PART_1"),
        SPEAKING_PART_2("SPEAKING", "PART_2"), SPEAKING_PART_3("SPEAKING", "PART_3");

        private final String skill;
        private final String taskType;

        Slot(String skill, String taskType) {
            this.skill = skill;
            this.taskType = taskType;
        }
    }

    /** What the service found for the paper. */
    public record PaperFacts(ExamStatus status, ExamType examType, CertificateType certificateType,
            CertificateVariant certificateVariant, boolean hasListening, boolean hasReading, boolean bandReady) {
    }

    /** What the service found for one task; null fields when the task does not exist. */
    public record TaskFacts(Slot slot, boolean exists, String skill, String taskType, String certificateType,
            boolean published, boolean hasImage) {

        public TaskFacts(Slot slot, boolean exists, String skill, String taskType, String certificateType,
                boolean published) {
            this(slot, exists, skill, taskType, certificateType, published, false);
        }
    }

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_variant", nullable = false)
    private CertificateVariant certificateVariant;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "writing_task1_id", nullable = false)
    private UUID writingTask1Id;

    @Column(name = "writing_task2_id", nullable = false)
    private UUID writingTask2Id;

    @Column(name = "speaking_part1_id", nullable = false)
    private UUID speakingPart1Id;

    @Column(name = "speaking_part2_id", nullable = false)
    private UUID speakingPart2Id;

    @Column(name = "speaking_part3_id", nullable = false)
    private UUID speakingPart3Id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExamStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected FullTest() {
    }

    public static FullTest draft(String title, String description, CertificateVariant variant, UUID examId,
            List<UUID> taskIdsInSlotOrder, UUID authorId) {
        FullTest test = new FullTest();
        test.id = UUID.randomUUID();
        test.status = ExamStatus.DRAFT;
        test.createdByUserId = authorId;
        test.apply(title, description, variant, examId, taskIdsInSlotOrder);
        return test;
    }

    public void update(String title, String description, CertificateVariant variant, UUID examId,
            List<UUID> taskIdsInSlotOrder) {
        if (status != ExamStatus.DRAFT && status != ExamStatus.REJECTED) {
            throw new ConflictException("FULL_TEST_NOT_EDITABLE",
                    "Only a draft or rejected full test can be edited; this one is %s".formatted(status));
        }
        apply(title, description, variant, examId, taskIdsInSlotOrder);
    }

    private void apply(String title, String description, CertificateVariant variant, UUID examId, List<UUID> taskIds) {
        if (variant != CertificateVariant.ACADEMIC && variant != CertificateVariant.GENERAL) {
            throw new BadRequestException("FULL_TEST_VARIANT_INVALID", "An IELTS full test is Academic or General");
        }
        if (taskIds.size() != Slot.values().length || taskIds.stream().distinct().count() != taskIds.size()) {
            throw new BadRequestException("FULL_TEST_TASKS_INVALID",
                    "A full test names five different tasks: Writing Task 1 and 2, Speaking Parts 1 to 3");
        }
        this.title = title;
        this.description = description == null ? "" : description;
        this.certificateVariant = variant;
        this.examId = examId;
        this.writingTask1Id = taskIds.get(0);
        this.writingTask2Id = taskIds.get(1);
        this.speakingPart1Id = taskIds.get(2);
        this.speakingPart2Id = taskIds.get(3);
        this.speakingPart3Id = taskIds.get(4);
    }

    /** The five task ids in {@link Slot} order. */
    public List<UUID> taskIds() {
        return List.of(writingTask1Id, writingTask2Id, speakingPart1Id, speakingPart2Id, speakingPart3Id);
    }

    public UUID taskId(Slot slot) {
        return taskIds().get(slot.ordinal());
    }

    public void submitForReview(PaperFacts paper, List<TaskFacts> tasks, Instant now) {
        if (status != ExamStatus.DRAFT && status != ExamStatus.REJECTED) {
            throw new ConflictException("FULL_TEST_NOT_SUBMITTABLE",
                    "Only a draft or rejected full test can be submitted; this one is %s".formatted(status));
        }
        requireSittable(paper, tasks);
        this.status = ExamStatus.PENDING_REVIEW;
        this.submittedForReviewAt = now;
    }

    /** Approving publishes, as it does for a paper; the components are checked again, they may have changed since. */
    public void approve(UUID reviewerId, PaperFacts paper, List<TaskFacts> tasks, Instant now) {
        requirePendingReview("approved");
        requireSittable(paper, tasks);
        this.status = ExamStatus.PUBLISHED;
        this.publishedAt = now;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = null;
    }

    public void reject(UUID reviewerId, String note, Instant now) {
        requirePendingReview("rejected");
        if (note == null || note.isBlank()) {
            throw new BadRequestException("FULL_TEST_REVIEW_NOTE_REQUIRED", "A rejection must say why");
        }
        this.status = ExamStatus.REJECTED;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = now;
        this.reviewNote = note.strip();
    }

    public void archive() {
        if (status == ExamStatus.ARCHIVED) {
            throw new ConflictException("FULL_TEST_ALREADY_ARCHIVED", "This full test is already archived");
        }
        this.status = ExamStatus.ARCHIVED;
    }

    private void requirePendingReview(String verb) {
        if (status != ExamStatus.PENDING_REVIEW) {
            throw new ConflictException("FULL_TEST_NOT_PENDING_REVIEW",
                    "Only a full test waiting on review can be %s; this one is %s".formatted(verb, status));
        }
    }

    /**
     * Learners can sit it: a published IELTS mock paper of this variant with both a Listening and a Reading section,
     * and in every slot a published IELTS task of the slot's skill and type.
     */
    public void requireSittable(PaperFacts paper, List<TaskFacts> tasks) {
        if (paper == null || paper.status() != ExamStatus.PUBLISHED || paper.examType() != ExamType.MOCK
                || paper.certificateType() != CertificateType.IELTS || paper.certificateVariant() != certificateVariant
                || !paper.hasListening() || !paper.hasReading()) {
            throw new ConflictException("FULL_TEST_PAPER_INVALID",
                    "The paper must be a published IELTS %s mock with a Listening and a Reading section"
                            .formatted(certificateVariant));
        }
        if (!paper.bandReady()) {
            throw new ConflictException("FULL_TEST_PAPER_NOT_BANDABLE",
                    "The paper must have 40 whole marks in each skill and a matching IELTS band conversion");
        }
        for (Slot slot : Slot.values()) {
            TaskFacts task = tasks.stream().filter(facts -> facts.slot() == slot).findFirst().orElse(null);
            if (task == null || !task.exists() || !task.published() || !slot.skill.equals(task.skill())
                    || !slot.taskType.equals(task.taskType()) || !"IELTS".equals(task.certificateType())) {
                throw new ConflictException("FULL_TEST_TASK_INVALID",
                        "%s must be a published IELTS %s %s task".formatted(slot, slot.skill, slot.taskType));
            }
            // Academic Task 1 describes a chart; General Training Task 1 is a letter and has none.
            if (slot == Slot.WRITING_TASK_1 && certificateVariant == CertificateVariant.ACADEMIC && !task.hasImage()) {
                throw new ConflictException("FULL_TEST_TASK1_NEEDS_CHART",
                        "An Academic full test's Writing Task 1 must have its chart");
            }
        }
    }
}
