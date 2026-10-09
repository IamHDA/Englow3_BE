package com.englow3.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewOption;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewPair;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion;
import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.command.SaveAuthoringCommand;
import com.englow3.quiz.entity.QuizQuestionType;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class QuizAuthoringIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    AdminQuizService service;
    @Autowired
    JdbcClient jdbc;
    UUID author, quiz;

    @BeforeEach
    void signIn() {
        author = new LearnerFixture(jdbc).learner();
        UUID authId = jdbc.sql("select auth_provider_id from users where id=:id").param("id", author).query(UUID.class)
                .single();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject(authId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
        if (quiz != null) {
            jdbc.sql("delete from quizzes where id=:id").param("id", quiz).update();
        }
        jdbc.sql("delete from users where id=:id").param("id", author).update();
    }

    CreateQuizCommand metadata(String title) {
        return new CreateQuizCommand("ux-" + author, title, "Description", "Grammar", "B1", 600, (short) 60);
    }

    List<NewQuestion> questions() {
        return List.of(
                new NewQuestion(QuizQuestionType.MULTIPLE_CHOICE, "Choose", "Choose the correct answer", (short) 1,
                        "Reason", null, null, null, null,
                        List.of(new NewOption("A", "yes", true), new NewOption("B", "no", false)), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()),
                new NewQuestion(QuizQuestionType.FILL_BLANK, "Fill", "Complete the sentence", (short) 1, "Reason", "I ",
                        " English", null, null, List.of(), List.of("study"), List.of(), List.of(), List.of(), List.of(),
                        List.of()),
                new NewQuestion(QuizQuestionType.REWRITE, "Rewrite", "Rewrite this sentence", (short) 1, "Reason", null,
                        null, "I am learning English", "learning", List.of(), List.of(),
                        List.of("I", "study", "English"), List.of("I", "study", "English"), List.of(), List.of(),
                        List.of()),
                new NewQuestion(QuizQuestionType.REORDER, "Reorder", "Put the words in order", (short) 1, "Reason",
                        null, null, null, null, List.of(), List.of(), List.of(), List.of(),
                        List.of("English", "study", "I"), List.of("I", "study", "English"), List.of()),
                new NewQuestion(QuizQuestionType.MATCHING, "Match", "Match the pairs", (short) 1, "Reason", null, null,
                        null, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(new NewPair("hello", "xin chao"), new NewPair("bye", "tam biet"))));
    }

    @Test
    void roundTripsAllFiveShapesAndRejectsAStaleWholeDocument() {
        var created = service.saveAuthoring(new SaveAuthoringCommand(null, null, metadata("Original"), questions()));
        quiz = created.id();
        assertThat(created.questions()).extracting(NewQuestion::questionType)
                .containsExactly(QuizQuestionType.values());
        assertThat(created.questions().get(3).correctOrder()).containsExactly("I", "study", "English");
        var updated = service.saveAuthoring(
                new SaveAuthoringCommand(quiz, created.version(), metadata("Updated"), List.of(questions().get(4))));
        assertThat(updated.questions()).hasSize(1);
        assertThatThrownBy(() -> service
                .saveAuthoring(new SaveAuthoringCommand(quiz, created.version(), metadata("Stale"), questions())))
                        .isInstanceOf(ConflictException.class).hasFieldOrPropertyWithValue("code", "CONTENT_CHANGED");
        assertThat(service.authoringDetail(quiz).metadata().title()).isEqualTo("Updated");
        assertThat(service.authoringDetail(quiz).questions()).hasSize(1);
    }

    @Test
    void invalidReplacementRollsBackBothMetadataAndDeletedQuestions() {
        var created = service.saveAuthoring(new SaveAuthoringCommand(null, null, metadata("Original"), questions()));
        quiz = created.id();
        var invalid = new NewQuestion(QuizQuestionType.MULTIPLE_CHOICE, "Bad", "No correct answer", (short) 1, null,
                null, null, null, null, List.of(new NewOption("A", "one", false), new NewOption("B", "two", false)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> service.saveAuthoring(
                new SaveAuthoringCommand(quiz, created.version(), metadata("Should roll back"), List.of(invalid))))
                        .isInstanceOf(BadRequestException.class);
        var after = service.authoringDetail(quiz);
        assertThat(after.metadata().title()).isEqualTo("Original");
        assertThat(after.version()).isEqualTo(created.version());
        assertThat(after.questions()).hasSize(5);
    }

    @Test
    void publishedQuestionsRemainFrozen() {
        var created = service.saveAuthoring(new SaveAuthoringCommand(null, null, metadata("Original"), questions()));
        quiz = created.id();
        service.publish(quiz);
        var current = service.authoringDetail(quiz);
        assertThatThrownBy(() -> service
                .saveAuthoring(new SaveAuthoringCommand(quiz, current.version(), metadata("Changed"), List.of())))
                        .isInstanceOf(ConflictException.class)
                        .hasFieldOrPropertyWithValue("code", "CONTENT_NOT_EDITABLE");
        assertThat(service.authoringDetail(quiz).questions()).hasSize(5);
    }
}
