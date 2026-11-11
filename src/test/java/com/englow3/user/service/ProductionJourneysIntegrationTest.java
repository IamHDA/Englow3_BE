package com.englow3.user.service;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.englow3.support.*;
import com.englow3.user.api.PlacementRecorder;
import com.englow3.user.entity.Role;
import com.englow3.exam.service.*;
import com.englow3.exam.dto.command.*;
import com.englow3.exam.entity.SectionType;
import com.englow3.quiz.service.AdminQuizService;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.assessment.service.*;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.entity.*;
import com.englow3.shared.error.*;

@Transactional
class ProductionJourneysIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcClient jdbc;
    @Autowired
    AdminUserService userAdmin;
    @Autowired
    AdminQuizService quizzes;
    @Autowired
    ScoreConfigurationService conversions;
    @Autowired
    ExamAttemptService exams;
    @Autowired
    AssessmentAuthoringService authoring;
    @Autowired
    AssessmentService assessment;
    @Autowired
    PlacementRecorder placements;
    @Autowired
    AccountService accounts;
    @Autowired
    WebApplicationContext web;
    LearnerFixture fixture;

    @BeforeEach
    void setup() {
        fixture = new LearnerFixture(jdbc);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    UUID person(String role) {
        UUID id = fixture.learner();
        jdbc.sql("update users set role=:role where id=:id").param("role", role).param("id", id).update();
        return id;
    }

    @Test
    void roleChangesAreAuditedAndRejectStaleUpdatesAndSelfDemotion() {
        UUID admin = person("ADMIN"), learner = person("LEARNER");
        SignedIn.as(jdbc, admin, "ADMIN");
        assertThat(userAdmin.changeRole(learner, Role.LEARNER, Role.STAFF, "Assign editorial duties").role())
                .isEqualTo(Role.STAFF);
        assertThat(userAdmin.roleHistory(learner)).singleElement().satisfies(e -> {
            assertThat(e.actorId()).isEqualTo(admin);
            assertThat(e.previousRole()).isEqualTo(Role.LEARNER);
        });
        assertThatThrownBy(() -> userAdmin.changeRole(learner, Role.LEARNER, Role.ADMIN, "Stale view"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> userAdmin.changeRole(admin, Role.ADMIN, Role.LEARNER, "Self demotion"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void privateDraftsAreScopedInListsAndCannotBeReadByOtherStaff() {
        UUID first = person("STAFF"), second = person("STAFF");
        UUID quiz = fixture.publishedQuiz("Private quiz " + first, first);
        jdbc.sql("update quizzes set status='DRAFT' where id=:id").param("id", quiz).update();
        SignedIn.as(jdbc, second, "STAFF");
        assertThatThrownBy(() -> quizzes.authoringDetail(quiz)).isInstanceOf(ForbiddenException.class);
        assertThat(quizzes.searchForAuthoring(QuizStatus.DRAFT, "Private quiz " + first, PageRequest.of(0, 20)))
                .isEmpty();
        SignedIn.as(jdbc, first, "STAFF");
        assertThat(quizzes.authoringDetail(quiz).id()).isEqualTo(quiz);
        SignedIn.as(jdbc, second, "ADMIN");
        assertThat(quizzes.authoringDetail(quiz).id()).isEqualTo(quiz);
    }

    @Test
    void aConfiguredFullToeicSittingPersistsItsConvertedTotal() {
        UUID admin = person("ADMIN");
        SignedIn.as(jdbc, admin, "ADMIN");
        var rows = new ArrayList<ConfigureToeicScoresCommand.Row>();
        for (var section : List.of(SectionType.LISTENING, SectionType.READING)) {
            rows.add(new ConfigureToeicScoresCommand.Row(section, 0, 99, new BigDecimal("5")));
            rows.add(new ConfigureToeicScoresCommand.Row(section, 100, 100, new BigDecimal("495")));
        }
        conversions.configure(new ConfigureToeicScoresCommand(LocalDate.now(ZoneOffset.UTC),
                "Test fixture only; not a production reference", rows));
        var papers = new ExamFixture(jdbc);
        UUID exam = papers.publishedExam("TOEIC fixture " + admin, admin);
        jdbc.sql("update exams set certificate_type='TOEIC' where id=:id").param("id", exam).update();
        var answers = new ArrayList<SubmitExamAttemptCommand.SubmittedAnswer>();
        int order = 1;
        for (String section : List.of("LISTENING", "READING")) {
            UUID set = papers.questionSet(papers.part(papers.section(exam, order++, section), 1, "Part"), 1);
            for (int i = 1; i <= 100; i++) {
                UUID q = papers.question(set, i, "Question " + i);
                UUID o = papers.option(q, 1, "Correct", true);
                answers.add(new SubmitExamAttemptCommand.SubmittedAnswer(q, List.of(o)));
            }
        }
        UUID learner = person("LEARNER");
        SignedIn.as(jdbc, learner);
        var attempt = exams.start(exam);
        var result = exams.submit(new SubmitExamAttemptCommand(attempt.id(), answers));
        assertThat(result.convertedScore()).isEqualByComparingTo("990");
        assertThat(exams.result(attempt.id()).convertedScore()).isEqualByComparingTo("990");
        assertThat(exams.attemptHistory(PageRequest.of(0, 10)).getContent().getFirst().convertedScore())
                .isEqualByComparingTo("990");
    }

    @Test
    void anAssignedReviewerCanGradeSomeoneElsesTaskAndTheAuthorCannotBypassAssignment() {
        UUID owner = person("STAFF"), reviewer = person("STAFF"), learner = person("LEARNER"), admin = person("ADMIN");
        SignedIn.as(jdbc, owner, "STAFF");
        var task = authoring.create(new AssessmentTaskCommand(AssessmentSkill.WRITING, "An opinion", "TASK_2",
                "Discuss learning languages.", "Use examples.", "Sample", 250, 2400, null, null, null));
        authoring.transition(task.id(), "submit", null);
        SignedIn.as(jdbc, admin, "ADMIN");
        authoring.transition(task.id(), "approve", null);
        SignedIn.as(jdbc, learner);
        var draft = assessment.start(task.id(), UUID.randomUUID(), null, null).attempt();
        assessment.saveDraft(draft.id(), "Learning languages helps people communicate clearly. ".repeat(60),
                draft.version());
        var submitted = assessment.submit(draft.id());
        SignedIn.as(jdbc, admin, "ADMIN");
        var assignment = authoring.assign(draft.id(), reviewer, submitted.version(), "Balance the grading queue");
        assertThat(assignment.history()).hasSize(1);
        SignedIn.as(jdbc, owner, "STAFF");
        assertThatThrownBy(() -> authoring.submission(draft.id())).isInstanceOf(ForbiddenException.class);
        SignedIn.as(jdbc, reviewer, "STAFF");
        assertThat(authoring.submission(draft.id()).id()).isEqualTo(draft.id());
        assertThat(
                authoring.searchSubmissions(AssessmentAttemptStatus.NEEDS_REVIEW, null, "", true, PageRequest.of(0, 20))
                        .getContent()).extracting(e -> e.id()).contains(draft.id());
        String report = """
                {"criteria":[{"key":"TASK_RESPONSE","score":6,"feedback":"An argument is present"},{"key":"COHERENCE_COHESION","score":6,"feedback":"Ideas connect"},{"key":"LEXICAL_RESOURCE","score":6,"feedback":"Vocabulary is clear"},{"key":"GRAMMATICAL_RANGE","score":6,"feedback":"Sentences are grammatical"}],"summary":"Practice more examples","strengths":["Clear ideas"],"improvements":["Expand support"]}
                """;
        assertThatThrownBy(() -> authoring.grade(draft.id(), report, "Review", null, submitted.version()))
                .isInstanceOf(ConflictException.class);
        assertThat(authoring.grade(draft.id(), report, "Reviewed the response", null, assignment.version()).status())
                .isEqualTo(AssessmentAttemptStatus.COMPLETED);
    }

    @Test
    void oldIeltsCallbacksCannotReplaceNewerMeasurementsAndExportIncludesPlans() {
        UUID learner = person("LEARNER");
        jdbc.sql(
                "insert into learner_profiles(id,user_id,current_level,target_certificate_type) values(:id,:u,'B1','IELTS')")
                .param("id", UUID.randomUUID()).param("u", learner).update();
        var papers = new ExamFixture(jdbc);
        UUID exam = papers.publishedExam("Placement " + learner, learner);
        SignedIn.as(jdbc, learner);
        UUID old = exams.start(exam).id();
        // Both callbacks can refer to one exam attempt; the measurement timestamp still fences the older one.
        Instant now = Instant.now();
        placements.recordIeltsBand(learner, old, "B2", new BigDecimal("6"), now, false);
        placements.recordIeltsBand(learner, old, "C1", new BigDecimal("7"), now.plusSeconds(1), true);
        placements.recordIeltsBand(learner, old, "A2", new BigDecimal("3"), now, true);
        assertThat(jdbc.sql("select current_score from learner_profiles where user_id=:u").param("u", learner)
                .query(BigDecimal.class).single()).isEqualByComparingTo("7");
        assertThat(accounts.exportData()).containsKeys("fullTestSittings", "roadmaps", "roadmapStages", "roadmapItems",
                "roadmapMilestones", "roadmapAdjustments", "levelChecks");
    }

    @Test
    void measureWarmHttpReadLatencyThroughSecurityAndRealDatabase() throws Exception {
        UUID admin = person("ADMIN");
        UUID learner = person("LEARNER");
        jdbc.sql(
                "insert into learner_profiles(id,user_id,current_level,target_level,daily_study_minutes) values(gen_random_uuid(),:user,'B1','B2',30)")
                .param("user", learner).update();
        jdbc.sql(
                "insert into users(id,auth_provider_id,email,role,full_name,display_name,onboarding_step) select gen_random_uuid(),gen_random_uuid(),'load-'||gen_random_uuid()::text||'@example.test','LEARNER','Load Fixture','Load Fixture '||n,'COMPLETED' from generate_series(1,5000) n")
                .update();
        UUID quiz = fixture.publishedQuiz("Load fixture " + learner, admin);
        jdbc.sql(
                "insert into quiz_attempts(id,quiz_id,user_id,status,started_at,expires_at,score,max_score,score_percentage,correct_answer_count,question_count,passed,submitted_at) select gen_random_uuid(),:quiz,:user,'SCORED',now()-n*interval '1 hour',now()-n*interval '1 hour'+interval '10 minutes',80,100,80,8,10,true,now()-n*interval '1 hour' from generate_series(1,5000) n")
                .param("quiz", quiz).param("user", learner).update();
        UUID auth = jdbc.sql("select auth_provider_id from users where id=:id").param("id", admin).query(UUID.class)
                .single();
        UUID learnerAuth = jdbc.sql("select auth_provider_id from users where id=:id").param("id", learner)
                .query(UUID.class).single();
        var mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        for (String path : List.of("/api/user/me/bootstrap", "/api/daily-path", "/api/exams?size=12",
                "/api/admin/users?size=20")) {
            var samples = new ArrayList<Double>();
            boolean privileged = path.startsWith("/api/admin/");
            var authority = new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    privileged ? "ROLE_ADMIN" : "ROLE_LEARNER");
            UUID subject = privileged ? auth : learnerAuth;
            for (int i = 0; i < 45; i++) {
                long start = System.nanoTime();
                mvc.perform(get(path).with(jwt().jwt(b -> b.subject(subject.toString())).authorities(authority)))
                        .andExpect(status().isOk());
                if (i >= 5)
                    samples.add((System.nanoTime() - start) / 1_000_000.0);
            }
            samples.sort(Comparator.naturalOrder());
            System.out.printf(Locale.ROOT, "LATENCY_LOCAL endpoint=%s samples=%d p50_ms=%.2f p95_ms=%.2f max_ms=%.2f%n",
                    path, samples.size(), samples.get(19), samples.get(37), samples.getLast());
        }
    }
}
