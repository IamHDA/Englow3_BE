package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * Matching headings on the real schema: the set's list reaches the learner without its key, the publish check reads
 * question_matching_answers, and a choice from the list is stored in attempt_answer_options' set-option column.
 */
class IeltsMatchingIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private ExamAttemptService attempts;

    @Autowired
    private QuestionRepository questionRepo;

    @Autowired
    private JdbcClient jdbc;

    private UUID examId;
    private UUID first;
    private UUID second;
    private UUID headingI;
    private UUID headingIi;

    @BeforeEach
    void matchingHeadings() {
        LearnerFixture learners = new LearnerFixture(jdbc);
        UUID author = learners.learner();
        UUID learner = learners.learner();
        ExamFixture exams = new ExamFixture(jdbc);
        examId = exams.publishedExam("IELTS Academic Reading", author);
        jdbc.sql("""
                update exams set certificate_type = 'IELTS', certificate_variant = 'ACADEMIC', max_raw_score = 2
                 where id = :id
                """).param("id", examId).update();
        UUID set = exams.questionSet(exams.part(exams.section(examId, 1, "READING"), 1, "Passage 1"), 1);
        headingI = entry(set, "i", "A new beginning", 1);
        headingIi = entry(set, "ii", "An old problem", 2);
        entry(set, "iii", "A surprising result", 3);
        first = matching(set, 1, headingIi);
        second = matching(set, 2, headingI);
        SignedIn.as(jdbc, learner);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    private UUID entry(UUID set, String label, String content, int orderNo) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into question_set_options (id, question_set_id, option_label, content, order_no)
                values (:id, :set, :label, :content, :orderNo)
                """).param("id", id).param("set", set).param("label", label).param("content", content)
                .param("orderNo", orderNo).update();
        return id;
    }

    private UUID matching(UUID set, int orderNo, UUID answer) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into questions (id, question_set_id, question_type, content, difficulty_level, skill_type,
                                       order_no, max_raw_score)
                values (:id, :set, 'MATCHING', :content, 'MEDIUM', 'READING', :orderNo, 1)
                """).param("id", id).param("set", set).param("content", "Paragraph " + orderNo)
                .param("orderNo", orderNo).update();
        jdbc.sql("insert into question_matching_answers (question_id, question_set_option_id) values (:q, :o)")
                .param("q", id).param("o", answer).update();
        return id;
    }

    @Test
    void aMatchingQuestionNeedsExactlyOneEntryAsItsAnswer() {
        assertThat(questionRepo.findIncompleteQuestionOrderNos(examId)).isEmpty();

        jdbc.sql("delete from question_matching_answers where question_id = :q").param("q", second).update();

        assertThat(questionRepo.findIncompleteQuestionOrderNos(examId)).containsExactly(2);
    }

    @Test
    void theLearnerChoosesFromTheSharedListAndIsGradedPerQuestion() {
        ExamAttemptResult sitting = attempts.start(StartExamAttemptCommand.full(examId));
        var set = attempts.paperForAttempt(sitting.id()).sections().getFirst().parts().getFirst().questionSets()
                .getFirst();
        assertThat(set.setOptions()).extracting(entry -> entry.label()).containsExactly("i", "ii", "iii");
        assertThat(set.questions()).allSatisfy(question -> {
            assertThat(question.options()).isEmpty();
            assertThat(question.choiceLimit()).isEqualTo(1);
        });

        ExamAttemptResult scored = attempts.submit(new SubmitExamAttemptCommand(sitting.id(), List
                .of(new SubmittedAnswer(first, List.of(headingIi)), new SubmittedAnswer(second, List.of(headingIi)))));

        assertThat(scored.rawScore()).isEqualByComparingTo("1");
        var reloaded = attempts.result(sitting.id()).questions();
        assertThat(reloaded).filteredOn(review -> review.questionId().equals(second)).singleElement()
                .satisfies(review -> {
                    assertThat(review.selectedOptionIds()).containsExactly(headingIi);
                    assertThat(review.correctOptionIds()).containsExactly(headingI);
                    assertThat(review.correct()).isFalse();
                });
        assertThat(jdbc.sql("""
                select count(*) from attempt_answer_options o join attempt_answers a on a.id = o.attempt_answer_id
                 where a.exam_attempt_id = :attempt and o.question_set_option_id is not null
                """).param("attempt", sitting.id()).query(Integer.class).single()).isEqualTo(2);
    }
}
