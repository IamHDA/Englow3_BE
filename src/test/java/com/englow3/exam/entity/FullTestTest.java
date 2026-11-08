package com.englow3.exam.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class FullTestTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private static List<UUID> fiveTasks() {
        return List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static FullTest draft() {
        return FullTest.draft("IELTS Academic Full Test 1", "", CertificateVariant.ACADEMIC, UUID.randomUUID(),
                fiveTasks(), UUID.randomUUID());
    }

    private static FullTest.PaperFacts goodPaper() {
        return new FullTest.PaperFacts(ExamStatus.PUBLISHED, ExamType.MOCK, CertificateType.IELTS,
                CertificateVariant.ACADEMIC, true, true, true);
    }

    private static List<FullTest.TaskFacts> goodTasks() {
        return List.of(task(FullTest.Slot.WRITING_TASK_1, "WRITING", "TASK_1"),
                task(FullTest.Slot.WRITING_TASK_2, "WRITING", "TASK_2"),
                task(FullTest.Slot.SPEAKING_PART_1, "SPEAKING", "PART_1"),
                task(FullTest.Slot.SPEAKING_PART_2, "SPEAKING", "PART_2"),
                task(FullTest.Slot.SPEAKING_PART_3, "SPEAKING", "PART_3"));
    }

    private static FullTest.TaskFacts task(FullTest.Slot slot, String skill, String type) {
        return new FullTest.TaskFacts(slot, true, skill, type, "IELTS", true, slot == FullTest.Slot.WRITING_TASK_1);
    }

    private static String code(Throwable error) {
        return error instanceof ConflictException conflict ? conflict.getCode()
                : ((BadRequestException) error).getCode();
    }

    @Test
    void aDraftWithAPublishedPaperAndFiveRightTasksGoesForReview() {
        FullTest test = draft();

        test.submitForReview(goodPaper(), goodTasks(), NOW);

        assertThat(test.getStatus()).isEqualTo(ExamStatus.PENDING_REVIEW);
        assertThat(test.getSubmittedForReviewAt()).isEqualTo(NOW);
    }

    @Test
    void approvingPublishes() {
        FullTest test = draft();
        test.submitForReview(goodPaper(), goodTasks(), NOW);

        test.approve(UUID.randomUUID(), goodPaper(), goodTasks(), NOW);

        assertThat(test.getStatus()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(test.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void aPublishedPaperMustBeAbleToProduceBothBands() {
        var paper = new FullTest.PaperFacts(ExamStatus.PUBLISHED, ExamType.MOCK, CertificateType.IELTS,
                CertificateVariant.ACADEMIC, true, true, false);
        assertThatThrownBy(() -> draft().submitForReview(paper, goodTasks(), NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_PAPER_NOT_BANDABLE");
    }

    @Test
    void namesFiveDifferentTasks() {
        UUID same = UUID.randomUUID();

        assertThatThrownBy(() -> FullTest.draft("t", "", CertificateVariant.ACADEMIC, UUID.randomUUID(),
                List.of(same, same, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), UUID.randomUUID()))
                        .extracting(FullTestTest::code).isEqualTo("FULL_TEST_TASKS_INVALID");
    }

    @Test
    void isAcademicOrGeneralTraining() {
        assertThatThrownBy(
                () -> FullTest.draft("t", "", CertificateVariant.LR, UUID.randomUUID(), fiveTasks(), UUID.randomUUID()))
                        .extracting(FullTestTest::code).isEqualTo("FULL_TEST_VARIANT_INVALID");
    }

    @Test
    void refusesAnUnpublishedPaper() {
        var paper = new FullTest.PaperFacts(ExamStatus.DRAFT, ExamType.MOCK, CertificateType.IELTS,
                CertificateVariant.ACADEMIC, true, true, true);

        assertThatThrownBy(() -> draft().submitForReview(paper, goodTasks(), NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_PAPER_INVALID");
    }

    @Test
    void refusesAPaperOfTheOtherVariant() {
        var paper = new FullTest.PaperFacts(ExamStatus.PUBLISHED, ExamType.MOCK, CertificateType.IELTS,
                CertificateVariant.GENERAL, true, true, true);

        assertThatThrownBy(() -> draft().submitForReview(paper, goodTasks(), NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_PAPER_INVALID");
    }

    @Test
    void refusesAPaperWithoutAReadingSection() {
        var paper = new FullTest.PaperFacts(ExamStatus.PUBLISHED, ExamType.MOCK, CertificateType.IELTS,
                CertificateVariant.ACADEMIC, true, false, true);

        assertThatThrownBy(() -> draft().submitForReview(paper, goodTasks(), NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_PAPER_INVALID");
    }

    @Test
    void refusesATaskInTheWrongSlot() {
        List<FullTest.TaskFacts> tasks = new ArrayList<>(goodTasks());
        tasks.set(0, task(FullTest.Slot.WRITING_TASK_1, "WRITING", "TASK_2"));

        assertThatThrownBy(() -> draft().submitForReview(goodPaper(), tasks, NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_TASK_INVALID");
    }

    @Test
    void refusesAnUnpublishedTask() {
        List<FullTest.TaskFacts> tasks = new ArrayList<>(goodTasks());
        tasks.set(3, new FullTest.TaskFacts(FullTest.Slot.SPEAKING_PART_2, true, "SPEAKING", "PART_2", "IELTS", false));

        assertThatThrownBy(() -> draft().submitForReview(goodPaper(), tasks, NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_TASK_INVALID");
    }

    @Test
    void refusesATaskThatIsNotIelts() {
        List<FullTest.TaskFacts> tasks = new ArrayList<>(goodTasks());
        tasks.set(1, new FullTest.TaskFacts(FullTest.Slot.WRITING_TASK_2, true, "WRITING", "TASK_2", null, true));

        assertThatThrownBy(() -> draft().submitForReview(goodPaper(), tasks, NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_TASK_INVALID");
    }

    @Test
    void onlyAPendingTestCanBeApproved() {
        assertThatThrownBy(() -> draft().approve(UUID.randomUUID(), goodPaper(), goodTasks(), NOW))
                .extracting(FullTestTest::code).isEqualTo("FULL_TEST_NOT_PENDING_REVIEW");
    }

    @Test
    void aRejectionMustSayWhyAndMakesItEditableAgain() {
        FullTest test = draft();
        test.submitForReview(goodPaper(), goodTasks(), NOW);

        assertThatThrownBy(() -> test.reject(UUID.randomUUID(), " ", NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_REVIEW_NOTE_REQUIRED");
        test.reject(UUID.randomUUID(), "Task 1 chart is unreadable", NOW);
        test.update("Renamed", "", CertificateVariant.ACADEMIC, test.getExamId(), fiveTasks());

        assertThat(test.getStatus()).isEqualTo(ExamStatus.REJECTED);
        assertThat(test.getTitle()).isEqualTo("Renamed");
    }

    @Test
    void aTestWaitingOnReviewCannotBeEdited() {
        FullTest test = draft();
        test.submitForReview(goodPaper(), goodTasks(), NOW);

        assertThatThrownBy(() -> test.update("x", "", CertificateVariant.ACADEMIC, test.getExamId(), fiveTasks()))
                .extracting(FullTestTest::code).isEqualTo("FULL_TEST_NOT_EDITABLE");
    }

    @Test
    void anAcademicTestsTaskOneMustHaveItsChart() {
        List<FullTest.TaskFacts> tasks = new ArrayList<>(goodTasks());
        tasks.set(0,
                new FullTest.TaskFacts(FullTest.Slot.WRITING_TASK_1, true, "WRITING", "TASK_1", "IELTS", true, false));

        assertThatThrownBy(() -> draft().submitForReview(goodPaper(), tasks, NOW)).extracting(FullTestTest::code)
                .isEqualTo("FULL_TEST_TASK1_NEEDS_CHART");
    }

    @Test
    void aGeneralTrainingTaskOneIsALetterWithoutAChart() {
        FullTest general = FullTest.draft("IELTS General 1", "", CertificateVariant.GENERAL, UUID.randomUUID(),
                fiveTasks(), UUID.randomUUID());
        List<FullTest.TaskFacts> tasks = new ArrayList<>(goodTasks());
        tasks.set(0,
                new FullTest.TaskFacts(FullTest.Slot.WRITING_TASK_1, true, "WRITING", "TASK_1", "IELTS", true, false));

        general.submitForReview(new FullTest.PaperFacts(ExamStatus.PUBLISHED, ExamType.MOCK, CertificateType.IELTS,
                CertificateVariant.GENERAL, true, true, true), tasks, NOW);

        assertThat(general.getStatus()).isEqualTo(ExamStatus.PENDING_REVIEW);
    }
}
