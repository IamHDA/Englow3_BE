package com.englow3.exam.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.exam.repository.QuestionRepository;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection;

/**
 * The three exam read models, against a real database.
 * <p>
 * Each assembles a five-level tree from five separate reads and stitches it back together in memory. Two things can go
 * wrong there and neither is visible to a mock: the order can be lost between the query and the grouping, and the
 * learner's copy can carry a field only the marker is entitled to.
 */
class ExamPaperQueryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private LearnerExamPaperQuery learnerPaper;

    @Autowired
    private AdminExamPaperQuery adminPaper;

    @Autowired
    private ExamGradingQuery grading;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private QuestionRepository questions;

    private ExamFixture exams;
    private UUID examId;
    private UUID rightOption;

    @BeforeEach
    void setUp() {
        UUID author = new LearnerFixture(jdbc).learner();
        exams = new ExamFixture(jdbc);

        examId = exams.publishedExam("Mock paper", author);
        UUID listening = exams.section(examId, 1, "LISTENING");
        UUID reading = exams.section(examId, 2, "READING");

        UUID partOne = exams.part(listening, 1, "Part 1");
        exams.part(reading, 1, "Part 2");

        UUID set = exams.questionSet(partOne, 1);
        UUID first = exams.question(set, 1, "What did the speaker say?");
        exams.question(set, 2, "Where are they?");

        rightOption = exams.option(first, 1, "The right one", true);
        exams.option(first, 2, "A wrong one", false);
        this.set = set;
    }

    private UUID set;

    @Nested
    class TheLearnersCopy {

        /**
         * The rule this projection exists for. Checked by walking the options rather than by naming a field, because
         * the point is that no correct-answer marker survives anywhere in what is sent - a field added later is covered
         * without anyone remembering to extend this.
         */
        @Test
        void carriesNoMarkerForWhichOptionIsRight() {
            var paper = learnerPaper.load(examId).orElseThrow();

            List<LearnerExamPaperProjection.Option> options = paper.sections().stream()
                    .flatMap(section -> section.parts().stream()).flatMap(part -> part.questionSets().stream())
                    .flatMap(set -> set.questions().stream()).flatMap(question -> question.options().stream()).toList();

            assertThat(options).isNotEmpty();
            for (var component : LearnerExamPaperProjection.Option.class.getRecordComponents()) {
                assertThat(component.getType()).as("component %s", component.getName()).isNotEqualTo(boolean.class);
            }
            assertThat(options).extracting(LearnerExamPaperProjection.Option::content).containsExactly("The right one",
                    "A wrong one");
        }

        /** The tree has to come back in the order it was authored, or question 2 is asked before question 1. */
        @Test
        void keepsEveryLevelOfTheTreeInOrder() {
            var paper = learnerPaper.load(examId).orElseThrow();

            assertThat(paper.sections()).hasSize(2);
            assertThat(paper.sections().get(0).parts()).singleElement()
                    .satisfies(part -> assertThat(part.title()).isEqualTo("Part 1"));
            assertThat(paper.sections().get(0).parts().get(0).questionSets().get(0).questions())
                    .extracting(LearnerExamPaperProjection.Question::orderNo).containsExactly(1, 2);
        }

        /** A section with nothing under it still appears - an empty part of a paper is a fact about the paper. */
        @Test
        void keepsASectionThatHasNothingUnderItYet() {
            var paper = learnerPaper.load(examId).orElseThrow();

            assertThat(paper.sections().get(1).parts()).singleElement()
                    .satisfies(part -> assertThat(part.questionSets()).isEmpty());
        }

        @Test
        void answersEmptyForAPaperThatDoesNotExist() {
            assertThat(learnerPaper.load(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    class TheAuthorsCopy {

        /** The same tree, and here the marker is the point - an author cannot review a key they cannot see. */
        @Test
        void carriesTheAnswerKeyAndTheExplanation() {
            var paper = adminPaper.loadForAdmin(examId).orElseThrow();

            var options = paper.sections().get(0).parts().get(0).questionSets().get(0).questions().get(0).options();

            assertThat(options).filteredOn(option -> option.id().equals(rightOption)).singleElement()
                    .satisfies(option -> {
                        assertThat(option.correct()).isTrue();
                        assertThat(option.explanation()).isNotBlank();
                    });
        }

        @Test
        void answersEmptyForAPaperThatDoesNotExist() {
            assertThat(adminPaper.loadForAdmin(UUID.randomUUID())).isEmpty();
        }
    }

    @Nested
    class Grading {

        /** The marker reads a flat list across the whole paper, in the order the questions are asked. */
        @Test
        void loadsEveryQuestionWithItsKey() {
            var questions = grading.load(examId);

            assertThat(questions).hasSize(2);
            assertThat(questions.get(0).options()).filteredOn(ExamGradingQuery.GradingOption::correct).singleElement()
                    .satisfies(option -> assertThat(option.id()).isEqualTo(rightOption));
        }

        /** A question nobody has given options to yet grades as unanswerable rather than throwing. */
        @Test
        void handsBackAQuestionThatHasNoOptions() {
            var questions = grading.load(examId);

            assertThat(questions.get(1).options()).isEmpty();
        }

        @Test
        void answersEmptyForAPaperWithNoQuestions() {
            UUID empty = exams.publishedExam("Empty paper", new LearnerFixture(jdbc).learner());

            assertThat(grading.load(empty)).isEmpty();
        }
    }

    @Nested
    class WhatPublishingRefuses {

        /** One option, and it the right one, is a question that answers itself - as unfinished as one with none. */
        @Test
        void flagsAQuestionWithASingleOptionAsIncomplete() {
            UUID lonely = exams.question(set, 3, "Only one way to answer");
            exams.option(lonely, 1, "The only one", true);

            assertThat(questions.findIncompleteQuestionOrderNos(examId)).containsExactly(2, 3);
        }
    }
}
