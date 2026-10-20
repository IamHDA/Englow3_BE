package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.LearnerFixture;
import com.englow3.support.SignedIn;
import com.englow3.quiz.api.SubmittedQuizContextReader;
import com.englow3.quiz.service.QuizAttemptService;
import com.englow3.quiz.dto.command.SubmitQuizAttemptCommand;
import com.englow3.tutor.service.TutorService;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.fasterxml.jackson.databind.ObjectMapper;

class TutorStudyContextIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcClient jdbc;
    @Autowired
    TutorService tutor;
    @Autowired
    QuizAttemptService quizzes;
    @Autowired
    SubmittedQuizContextReader context;

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(tutor, "enabled", false);
        SignedIn.out();
    }

    @Test
    void onlyTheOwnersScoredQuestionCanEnterTheTutorUserContext() throws Exception {
        var fixture = new LearnerFixture(jdbc);
        UUID user = fixture.learner();
        SignedIn.as(jdbc, user);
        ReflectionTestUtils.setField(tutor, "enabled", true);
        UUID quiz = fixture.publishedQuiz("Synthetic review", user), question = UUID.randomUUID(),
                right = UUID.randomUUID();
        jdbc.sql(
                "insert into quiz_questions(id,quiz_id,order_no,question_type,title,prompt,points,explanation) values(:id,:quiz,1,'MULTIPLE_CHOICE','Choose','She ___ here since 2020.',1,'Use present perfect for a starting point.')")
                .param("id", question).param("quiz", quiz).update();
        jdbc.sql(
                "insert into quiz_question_options(id,quiz_question_id,order_no,label,content,correct) values(:id,:question,1,'A','has lived',true)")
                .param("id", right).param("question", question).update();
        var attempt = quizzes.start(quiz);
        assertThatThrownBy(() -> context.read(attempt.id(), question)).hasMessageContaining("not been scored");
        assertThatThrownBy(() -> tutor.send(new SendTutorMessageCommand(null, "Explain my answer.", null,
                UUID.randomUUID(), "GENERAL", false, attempt.id(), question))).hasMessageContaining("not been scored");
        quizzes.submit(new SubmitQuizAttemptCommand(attempt.id(),
                List.of(new SubmitQuizAttemptCommand.SubmittedAnswer(question, right.toString()))));
        assertThat(context.read(attempt.id(), question).correctAnswer()).contains("has lived");
        var accepted = tutor.send(new SendTutorMessageCommand(null, "Explain my answer.", null, UUID.randomUUID(),
                "GENERAL", false, attempt.id(), question));
        var payload = jdbc.sql("select input_payload::text from ai_jobs where target_id=:id")
                .param("id", accepted.messages().get(1).id()).query(String.class).single();
        var data = new ObjectMapper().readTree(payload);
        assertThat(data.path("systemPrompt").asText()).doesNotContain("has lived");
        assertThat(data.path("messages").toString()).contains("has lived");
        SignedIn.as(jdbc, fixture.learner());
        assertThatThrownBy(() -> context.read(attempt.id(), question)).hasMessageContaining("quiz attempt");
    }
}
