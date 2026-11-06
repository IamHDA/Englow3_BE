package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * An IELTS Listening section of 40 typed answers, end to end on the real schema: the publish check reads the accepted
 * answers, the paper numbers the questions, and the score is converted to a band through the seeded IELTS table.
 */
class IeltsSittingIntegrationTest extends PostgresIntegrationTest {

    private static final int QUESTIONS = 40;

    @Autowired
    private ExamAttemptService attempts;

    @Autowired
    private QuestionRepository questionRepo;

    @Autowired
    private JdbcClient jdbc;

    private UUID examId;
    private final List<UUID> questions = new ArrayList<>();

    @BeforeEach
    void anIeltsListeningPaper() {
        LearnerFixture learners = new LearnerFixture(jdbc);
        UUID author = learners.learner();
        UUID learner = learners.learner();
        ExamFixture exams = new ExamFixture(jdbc);
        examId = exams.publishedExam("IELTS Academic Listening", author);
        jdbc.sql("""
                update exams set certificate_type = 'IELTS', certificate_variant = 'ACADEMIC', max_raw_score = 40
                 where id = :id
                """).param("id", examId).update();
        UUID part = exams.part(exams.section(examId, 1, "LISTENING"), 1, "Part 1");
        UUID set = exams.questionSet(part, 1);
        for (int number = 1; number <= QUESTIONS; number++) {
            UUID question = UUID.randomUUID();
            jdbc.sql("""
                    insert into questions (id, question_set_id, question_type, content, difficulty_level, skill_type,
                                           order_no, max_raw_score)
                    values (:id, :setId, 'TEXT_ENTRY', 'Name: {{1}}', 'MEDIUM', 'LISTENING', :orderNo, 1)
                    """).param("id", question).param("setId", set).param("orderNo", number).update();
            jdbc.sql("""
                    insert into question_accepted_answers (id, question_id, answer_text, order_no)
                    values (:id, :questionId, 'museum', 1)
                    """).param("id", UUID.randomUUID()).param("questionId", question).update();
            questions.add(question);
        }
        SignedIn.as(jdbc, learner);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void aTextQuestionWithAnAcceptedAnswerIsCompleteAndOneWithoutIsNot() {
        assertThat(questionRepo.findIncompleteQuestionOrderNos(examId)).isEmpty();

        jdbc.sql("delete from question_accepted_answers where question_id = :id").param("id", questions.get(4))
                .update();

        assertThat(questionRepo.findIncompleteQuestionOrderNos(examId)).containsExactly(5);
    }

    @Test
    void thirtyCorrectAnswersOutOfFortyAreBandSevenAndTheBandIsKept() {
        ExamAttemptResult sitting = attempts.start(StartExamAttemptCommand.full(examId));
        LearnerExamPaperResult paper = attempts.paperForAttempt(sitting.id());
        var numbered = paper.sections().getFirst().parts().getFirst().questionSets().getFirst().questions();
        assertThat(numbered.getFirst().numberFrom()).isEqualTo(1);
        assertThat(numbered.getLast().numberTo()).isEqualTo(QUESTIONS);
        assertThat(numbered).allSatisfy(question -> assertThat(question.choiceLimit()).isZero());

        List<SubmittedAnswer> answers = new ArrayList<>();
        for (int i = 0; i < QUESTIONS; i++) {
            answers.add(new SubmittedAnswer(questions.get(i), List.of(), i < 30 ? " Museum " : "musuem"));
        }
        ExamAttemptResult scored = attempts.submit(new SubmitExamAttemptCommand(sitting.id(), answers));

        assertThat(scored.rawScore()).isEqualByComparingTo("30");
        assertThat(scored.sections()).singleElement().satisfies(section -> {
            assertThat(section.sectionType()).isEqualTo(SectionType.LISTENING);
            assertThat(section.band()).isEqualByComparingTo("7.0");
        });
        assertThat(attempts.result(sitting.id()).sections()).singleElement()
                .satisfies(section -> assertThat(section.band()).isEqualByComparingTo("7.0"));
        assertThat(attempts.result(sitting.id()).questions().getFirst().textAnswer()).isEqualTo(" Museum ");
    }
}
