package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.StartExamAttemptCommand.OpenAttempt;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.query.ExamOutlineQuery.OutlinePart;
import com.englow3.progress.query.DailyPathQuery;
import com.englow3.shared.error.DomainException;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * Practice and full sittings, end to end against a real database: what a practice is made of, what it is graded on, how
 * it meets an attempt that is already open, and what it is kept out of.
 */
class ExamPracticeIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ExamAttemptService attempts;

    @Autowired
    private LearnerExamService catalogue;

    @Autowired
    private DailyPathQuery progress;

    @Autowired
    private JdbcClient jdbc;

    private UUID learner;
    private UUID examId;
    private UUID partOne;
    private UUID partTwo;
    private UUID partFive;
    private UUID firstQuestion;
    private UUID firstRight;
    private UUID secondQuestion;
    private UUID secondWrong;
    private UUID readingQuestion;
    private UUID readingRight;

    @BeforeEach
    void setUp() {
        LearnerFixture learners = new LearnerFixture(jdbc);
        UUID author = learners.learner();
        learner = learners.learner();
        ExamFixture exams = new ExamFixture(jdbc);

        examId = exams.publishedExam("Practice paper", author);
        UUID listening = exams.section(examId, 1, "LISTENING");
        UUID reading = exams.section(examId, 2, "READING");
        partOne = exams.part(listening, 1, "Part 1: Photographs");
        partTwo = exams.part(listening, 2, "Part 2: Question-Response");
        partFive = exams.part(reading, 1, "Part 5: Incomplete Sentences");

        UUID photographs = exams.questionSet(partOne, 1);
        firstQuestion = exams.question(photographs, 1, "What is the man doing?");
        firstRight = exams.option(firstQuestion, 1, "Reading", true);
        exams.option(firstQuestion, 2, "Sleeping", false);
        secondQuestion = exams.question(photographs, 2, "Where is the bag?");
        exams.option(secondQuestion, 1, "On the chair", true);
        secondWrong = exams.option(secondQuestion, 2, "Under the desk", false);

        UUID responses = exams.questionSet(partTwo, 1);
        UUID responseQuestion = exams.question(responses, 1, "When does it open?");
        exams.option(responseQuestion, 1, "At nine", true);

        UUID sentences = exams.questionSet(partFive, 1);
        readingQuestion = exams.question(sentences, 1, "She ___ to work.");
        readingRight = exams.option(readingQuestion, 1, "walks", true);
        exams.option(readingQuestion, 2, "walk", false);

        SignedIn.as(jdbc, learner);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    private ExamAttemptResult practise(Set<UUID> parts, Integer minutes, OpenAttempt onOpen) {
        return attempts.start(new StartExamAttemptCommand(examId, ExamAttemptMode.PRACTICE, parts, minutes, onOpen));
    }

    private static String codeOf(Throwable failure) {
        return ((DomainException) failure).getCode();
    }

    @Test
    void theOutlineListsEveryPartWithItsQuestionCountInPaperOrder() {
        List<OutlinePart> outline = catalogue.outline(examId);

        assertThat(outline).extracting(OutlinePart::id).containsExactly(partOne, partTwo, partFive);
        assertThat(outline).extracting(OutlinePart::questionCount).containsExactly(2L, 1L, 1L);
    }

    @Test
    void aPracticeIsTotalledAndDeliveredOverTheChosenPartsOnly() {
        ExamAttemptResult started = practise(Set.of(partOne), null, null);

        assertThat(started.mode()).isEqualTo(ExamAttemptMode.PRACTICE);
        assertThat(started.questionCount()).isEqualTo(2);
        assertThat(started.maxRawScore()).isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(started.timeLimitSeconds()).isNull();
        assertThat(Duration.between(started.startedAt(), started.expiresAt())).isEqualTo(Duration.ofHours(24));
        assertThat(started.parts()).extracting(ExamAttemptResult.AttemptPart::title)
                .containsExactly("Part 1: Photographs");

        LearnerExamPaperResult paper = attempts.paperForAttempt(started.id());
        assertThat(paper.sections()).hasSize(1);
        assertThat(paper.sections().getFirst().parts()).extracting(LearnerExamPaperResult.LearnerPart::id)
                .containsExactly(partOne);
    }

    @Test
    void aPracticeIsGradedOutOfItsOwnPartsAndRefusesAnswersFromOtherParts() {
        ExamAttemptResult started = practise(Set.of(partOne), 10, null);
        assertThat(started.timeLimitSeconds()).isEqualTo(600);

        assertThatThrownBy(() -> attempts.submit(new SubmitExamAttemptCommand(started.id(),
                List.of(new SubmittedAnswer(readingQuestion, List.of(readingRight))))))
                        .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("QUESTION_NOT_IN_EXAM"));

        ExamAttemptResult scored = attempts.submit(new SubmitExamAttemptCommand(started.id(),
                List.of(new SubmittedAnswer(firstQuestion, List.of(firstRight)),
                        new SubmittedAnswer(secondQuestion, List.of(secondWrong)))));

        assertThat(scored.status()).isEqualTo(ExamAttemptStatus.SCORED);
        assertThat(scored.scorePercentage()).isEqualByComparingTo("50.00");
        assertThat(scored.questions()).hasSize(2);
    }

    @Test
    void anOpenAttemptOfAnotherKindIsNeitherReplacedNorReusedWithoutBeingAsked() {
        ExamAttemptResult practice = practise(Set.of(partOne), null, null);

        assertThat(practise(Set.of(partOne), null, null).resumed()).isTrue();
        assertThatThrownBy(() -> attempts.start(StartExamAttemptCommand.full(examId)))
                .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("ATTEMPT_IN_PROGRESS"));
        assertThatThrownBy(() -> practise(Set.of(partFive), null, null))
                .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("ATTEMPT_IN_PROGRESS"));

        ExamAttemptResult resumed = attempts
                .start(new StartExamAttemptCommand(examId, ExamAttemptMode.FULL, Set.of(), null, OpenAttempt.RESUME));
        assertThat(resumed.id()).isEqualTo(practice.id());

        ExamAttemptResult full = attempts
                .start(new StartExamAttemptCommand(examId, ExamAttemptMode.FULL, Set.of(), null, OpenAttempt.REPLACE));
        assertThat(full.id()).isNotEqualTo(practice.id());
        assertThat(full.mode()).isEqualTo(ExamAttemptMode.FULL);
        assertThat(full.questionCount()).isEqualTo(4);
        assertThat(attempts.result(practice.id()).status()).isEqualTo(ExamAttemptStatus.SCORED);
    }

    @Test
    void onlyAFullSittingCountsTowardExperienceAndTheBestScore() {
        Instant before = Instant.now().minusSeconds(60);
        ExamAttemptResult practice = practise(Set.of(partFive), null, null);
        attempts.submit(new SubmitExamAttemptCommand(practice.id(),
                List.of(new SubmittedAnswer(readingQuestion, List.of(readingRight)))));

        assertThat(progress.activityTotals(learner, before).examAttempts()).isZero();
        assertThat(catalogue.detail(examId).bestScorePercentage()).isNull();

        ExamAttemptResult full = attempts.start(StartExamAttemptCommand.full(examId));
        attempts.submit(new SubmitExamAttemptCommand(full.id(),
                List.of(new SubmittedAnswer(firstQuestion, List.of(firstRight)))));

        assertThat(progress.activityTotals(learner, before).examAttempts()).isEqualTo(1);
        // A full sitting is scored out of the paper's own maximum (200 in the fixture), not the sum of its questions.
        assertThat(catalogue.detail(examId).bestScorePercentage()).isEqualByComparingTo("0.50");
    }

    @Test
    void aPracticeMustNameAtLeastOnePartOfThisPaper() {
        assertThatThrownBy(() -> practise(Set.of(), null, null))
                .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("PRACTICE_PARTS_REQUIRED"));
        assertThatThrownBy(() -> practise(Set.of(UUID.randomUUID()), null, null))
                .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("PART_NOT_IN_EXAM"));
        assertThatThrownBy(() -> practise(Set.of(partOne), 301, null))
                .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("PRACTICE_TIME_LIMIT_INVALID"));
    }

    @Test
    void aPlacementTestCanOnlyBeTakenInFull() {
        jdbc.sql("update exams set exam_type = 'PLACEMENT' where id = :id").param("id", examId).update();

        assertThatThrownBy(() -> practise(Set.of(partOne), null, null))
                .satisfies(failure -> assertThat(codeOf(failure)).isEqualTo("PRACTICE_NOT_AVAILABLE"));
    }
}
