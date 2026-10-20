package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.*;
import com.englow3.assessment.api.SubmittedAssessmentContextReader;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.entity.*;
import com.englow3.assessment.repository.*;
import com.englow3.exam.api.SubmittedExamContextReader;
import com.englow3.exam.service.ExamAttemptService;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.tutor.service.TutorService;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.fasterxml.jackson.databind.ObjectMapper;

class TutorCompletedContextIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcClient jdbc;
    @Autowired
    AssessmentTaskRepository tasks;
    @Autowired
    AssessmentAttemptRepository assessments;
    @Autowired
    SubmittedAssessmentContextReader assessmentContext;
    @Autowired
    SubmittedExamContextReader examContext;
    @Autowired
    ExamAttemptService exams;
    @Autowired
    TutorService tutor;

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(tutor, "enabled", false);
        SignedIn.out();
    }

    @ParameterizedTest
    @EnumSource(AssessmentSkill.class)
    void completedAssessmentContextNeverIncludesAudioIdentityOrHiddenScores(AssessmentSkill skill) throws Exception {
        var fixture = new LearnerFixture(jdbc);
        UUID user = fixture.learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(tutor, "enabled", true);
        var task = tasks.saveAndFlush(AssessmentTask.draft(new AssessmentTaskCommand(skill, "Synthetic context",
                skill == AssessmentSkill.WRITING ? "TASK_2" : "PART_2", "Explain a favourite activity.",
                "Private rubric", "HIDDEN_SAMPLE", 1, 60), user));
        var mapper = new ObjectMapper();
        String snapshot = mapper
                .writeValueAsString(com.englow3.assessment.dto.result.AssessmentTaskResult.from(task, true));
        var attempt = assessments.saveAndFlush(AssessmentAttempt.draft(UUID.randomUUID(), user, task.getId(),
                UUID.randomUUID(), skill, snapshot, skill == AssessmentSkill.SPEAKING ? "PRIVATE_AUDIO_KEY" : null,
                skill == AssessmentSkill.SPEAKING ? "audio/wav" : null,
                skill == AssessmentSkill.SPEAKING ? 100L : null));
        UUID assessmentId = attempt.getId();
        assertThatThrownBy(() -> assessmentContext.read(assessmentId)).hasMessageContaining("not been assessed");
        attempt.saveAnswer("Written practice response", attempt.getVersion());
        attempt.submit(false, Instant.now());
        attempt.finishHuman(
                "{\"overall\":6.5,\"summary\":\"Explain past tense\",\"criteria\":[{\"key\":\"GRAMMAR\",\"score\":8.5,\"feedback\":\"Check verb forms\"}],\"reviewerEmail\":\"PRIVATE_EMAIL\",\"privateNote\":\"PRIVATE_NOTE\"}",
                "Spoken practice response", Instant.now(), false);
        attempt = assessments.saveAndFlush(attempt);
        var context = assessmentContext.read(attempt.getId());
        assertThat(context.reportedOverallBand()).isEqualTo(6.5);
        assertThat(context.learnerResponse()).isEqualTo(
                skill == AssessmentSkill.SPEAKING ? "Spoken practice response" : "Written practice response");
        assertThat(context.toString()).contains("Check verb forms").doesNotContain("8.5", "PRIVATE_AUDIO_KEY",
                "HIDDEN_SAMPLE", "PRIVATE_EMAIL", "PRIVATE_NOTE");
        UUID id = attempt.getId();
        var sent = tutor.send(new SendTutorMessageCommand(null, "Help me improve this completed work.", null,
                UUID.randomUUID(), "GENERAL", false, null, null, id, null, null));
        var payload = jdbc.sql("select input_payload::text from ai_jobs where target_id=:id")
                .param("id", sent.messages().get(1).id()).query(String.class).single();
        assertThat(mapper.readTree(payload).path("systemPrompt").asText()).doesNotContain("Check verb forms");
        assertThat(mapper.readTree(payload).path("messages").toString()).contains("Check verb forms")
                .doesNotContain("PRIVATE_AUDIO_KEY", "PRIVATE_EMAIL");
        SignedIn.as(jdbc, fixture.learner());
        assertThatThrownBy(() -> assessmentContext.read(id)).hasMessageContaining("not found");
    }

    @Test
    void examContextChecksCompletionOwnershipAndSelectedQuestionBeforeKeysAreRead() {
        var learner = new LearnerFixture(jdbc);
        UUID user = learner.learner();
        SignedIn.as(jdbc, user);
        var fixture = new ExamFixture(jdbc);
        UUID exam = fixture.publishedExam("Synthetic review", user);
        UUID section = fixture.section(exam, 1, "READING"), part = fixture.part(section, 1, "Reading"),
                set = fixture.questionSet(part, 1);
        UUID question = fixture.question(set, 1, "She ___ here since 2020."),
                right = fixture.option(question, 1, "has lived", true);
        fixture.option(question, 2, "live", false);
        jdbc.sql(
                "update question_sets set content='She moved here in 2020 and still lives here.',audio_object_key='PRIVATE_EXAM_AUDIO' where id=:id")
                .param("id", set).update();
        var attempt = exams.start(exam);
        assertThatThrownBy(() -> examContext.read(attempt.id(), question)).hasMessageContaining("not been scored");
        exams.submit(new SubmitExamAttemptCommand(attempt.id(),
                List.of(new SubmitExamAttemptCommand.SubmittedAnswer(question, List.of(right)))));
        assertThat(examContext.read(attempt.id(), question).correctAnswers()).contains("has lived");
        assertThat(examContext.read(attempt.id(), question).awardedRawScore()).isEqualByComparingTo("1");
        assertThat(examContext.read(attempt.id(), question).passage()).contains("2020");
        assertThat(examContext.read(attempt.id(), question).audioNotIncluded()).isTrue();
        assertThat(examContext.read(attempt.id(), question).toString()).doesNotContain("PRIVATE_EXAM_AUDIO");
        assertThatThrownBy(() -> examContext.read(attempt.id(), UUID.randomUUID())).hasMessageContaining("not in this");
        SignedIn.as(jdbc, learner.learner());
        assertThatThrownBy(() -> examContext.read(attempt.id(), question)).hasMessageContaining("not found");
    }
}
