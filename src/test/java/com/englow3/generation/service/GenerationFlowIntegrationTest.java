package com.englow3.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.GenerationClient;
import com.englow3.generation.entity.GenerationKind;
import com.englow3.generation.entity.GenerationStatus;
import com.englow3.generation.worker.ContentGenerationHandler;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A generation request from the press of the button to a set waiting for review, on the real schema and the real exam
 * and assessment writers. The AI service is a stand-in: each writing step returns a component, one of them wrong the
 * first time; assembling returns the pipeline-built sample set (src/test/resources/ielts/ai-sample-set) that
 * IeltsTrialPaperIntegrationTest already takes through review. The test plays the worker: it runs each pending job and
 * records it the way the worker does, in one transaction.
 */
class GenerationFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String SET = "ielts/ai-sample-set/";

    @MockitoBean
    private GenerationClient client;

    @MockitoBean
    private ObjectStorageClient storage;

    @Autowired
    private GenerationService generation;

    @Autowired
    private GenerationArtifacts artifacts;

    @Autowired
    private ContentGenerationHandler handler;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcClient jdbc;

    private UUID staff;
    private final List<JsonNode> stepBodies = new ArrayList<>();

    @BeforeEach
    void aStaffMemberAndAStandInAiService() throws Exception {
        staff = new LearnerFixture(jdbc).learner();
        jdbc.sql("update users set role = 'STAFF' where id = :id").param("id", staff).update();
        when(storage.presignGet(any(), any(), any())).thenReturn(java.net.URI.create("https://storage.test/x").toURL());

        when(client.step(anyString())).thenAnswer(call -> {
            JsonNode body = mapper.readTree((String) call.getArgument(0));
            stepBodies.add(body);
            String step = body.path("step").asText();
            ObjectNode reply = mapper.createObjectNode().put("topic", step);
            if (step.equals("plan")) {
                reply.put("title", "Rivers and cities");
            }
            ObjectNode out = mapper.createObjectNode().put("model", "stand-in");
            out.set("reply", reply);
            // Listening Part 1 is wrong the first time, so the problems go back to the model.
            boolean wrong = step.equals("listening-1") && !body.has("previous");
            out.set("problems", wrong ? mapper.valueToTree(List.of("Listening Part 1: give 2 speakers."))
                    : mapper.createArrayNode());
            return out.toString();
        });
        when(client.assemble(anyString())).thenAnswer(call -> {
            ObjectNode out = mapper.createObjectNode();
            out.set("paper", mapper.readTree(read(SET + "paper.json")));
            out.set("tasks", mapper.readTree(read(SET + "tasks.json")));
            out.put("chart_png_base64", Base64.getEncoder().encodeToString(read(SET + "task1-chart.png")));
            ObjectNode listening = out.putObject("listening");
            for (int part = 1; part <= 4; part++) {
                listening.set(String.valueOf(part), mapper.valueToTree(List.of(
                        Map.of("speaker", "Anna", "text", "Part " + part + " opens.", "voice", "en-GB-SoniaNeural"))));
            }
            out.putArray("problems");
            return out.toString();
        });
        byte[] mp3 = { (byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4, 0, 0, 0, 0 };
        when(client.audio(anyString())).thenReturn(
                mapper.createObjectNode().put("mp3_base64", Base64.getEncoder().encodeToString(mp3)).toString());
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void aRequestBecomesAPaperFiveTasksAndAFullTestWaitingForReview() {
        SignedIn.as(jdbc, staff, "STAFF");
        var request = generation.request(GenerationKind.IELTS_FULL_TEST, "Rivers, please");
        assertThat(generation.allowance().remaining()).isEqualTo(2);
        SignedIn.out(); // the worker has no signed-in user

        int jobs = runAllJobs(request.id());

        var done = generation.detail(request.id());
        assertThat(done.status()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(done.title()).isEqualTo("Rivers and cities");
        assertThat(jobs).isEqualTo(19); // 18 steps, one of them twice

        // The wrong Listening Part 1 went back with its problems, and the brief reached every writing step.
        JsonNode retry = stepBodies.stream().filter(b -> b.path("step").asText().equals("listening-1"))
                .filter(b -> b.has("previous")).findFirst().orElseThrow();
        assertThat(retry.path("previous").path("problems").get(0).asText()).contains("give 2 speakers");
        assertThat(stepBodies).allSatisfy(b -> assertThat(b.path("brief").asText()).isEqualTo("Rivers, please"));

        assertThat(status("exams", done.examId())).isEqualTo("PENDING_REVIEW");
        assertThat(column("select created_by_user_id::text from exams where id = :id", done.examId()))
                .isEqualTo(staff.toString());
        assertThat(jdbc.sql("""
                select count(*) from section_parts p join exam_sections s on s.id = p.exam_section_id
                 where s.exam_id = :id and s.section_type = 'LISTENING' and p.audio_object_key like :prefix
                """).param("id", done.examId()).param("prefix", done.examId() + "/audios/%").query(Long.class).single())
                .isEqualTo(4);

        assertThat(done.taskIds()).hasSize(5);
        for (UUID task : done.taskIds()) {
            assertThat(status("assessment_tasks", task)).isEqualTo("PENDING_REVIEW");
        }
        assertThat(column("select image_object_key from assessment_tasks where id = :id", done.taskIds().getFirst()))
                .startsWith("assessment-tasks/" + done.taskIds().getFirst());

        assertThat(status("full_tests", done.fullTestId())).isEqualTo("DRAFT");
        assertThat(column("select exam_id::text from full_tests where id = :id", done.fullTestId()))
                .isEqualTo(done.examId().toString());
    }

    @Test
    void threeRequestsADayCountOnlyThoseThatDidNotFail() {
        insertRequest("COMPLETED");
        insertRequest("FAILED");
        insertRequest("FAILED");
        insertRequest("COMPLETED");
        SignedIn.as(jdbc, staff, "STAFF");
        assertThat(generation.allowance().remaining()).isEqualTo(1);

        generation.request(GenerationKind.IELTS_FULL_TEST, null);

        jdbc.sql("update generation_requests set status = 'COMPLETED' where requested_by_user_id = :u")
                .param("u", staff).update();
        assertThatThrownBy(() -> generation.request(GenerationKind.IELTS_FULL_TEST, null))
                .isInstanceOf(ConflictException.class).hasMessageContaining("today's 3 requests");
    }

    @Test
    void aSecondRequestWaitsForTheFirstToFinish() {
        SignedIn.as(jdbc, staff, "STAFF");
        generation.request(GenerationKind.IELTS_FULL_TEST, null);

        assertThatThrownBy(() -> generation.request(GenerationKind.IELTS_FULL_TEST, null))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("GENERATION_IN_PROGRESS");
        assertThat(generation.allowance().inProgress()).isTrue();
    }

    /** Plays the worker until no job of this request is pending. Returns how many it ran. */
    private int runAllJobs(UUID requestId) {
        return runUntil(requestId, null);
    }

    private int runUntil(UUID requestId, String stopBefore) {
        int ran = 0;
        while (true) {
            var job = jdbc.sql("""
                    select id, target_id, input_payload::text as payload from ai_jobs
                     where job_type = 'CONTENT_GENERATION' and status = 'PENDING' and target_id = :request
                     order by created_at limit 1
                    """).param("request", requestId).query((rs, n) -> new Object[] { rs.getObject("id", UUID.class),
                    rs.getObject("target_id", UUID.class), rs.getString("payload") }).optional();
            if (job.isEmpty()) {
                return ran;
            }
            UUID id = (UUID) job.get()[0];
            UUID target = (UUID) job.get()[1];
            String payload = (String) job.get()[2];
            if (stopBefore != null && payload.contains("\"" + stopBefore + "\"")) {
                return ran;
            }
            AiJobHandler.Outcome outcome = handler.run(id, target, payload);
            assertThat(outcome.success()).as("job %s: %s", payload, outcome.errorMessage()).isTrue();
            transactions.executeWithoutResult(tx -> {
                handler.complete(target, payload, outcome.outputPayload());
                jdbc.sql("update ai_jobs set status = 'COMPLETED' where id = :id").param("id", id).update();
            });
            ran++;
            assertThat(ran).as("runaway job loop").isLessThan(40);
        }
    }

    @Test
    void assemblyAndItsCheckpointSurviveLostJobAcknowledgementWithoutDuplicateDrafts() throws Exception {
        SignedIn.as(jdbc, staff, "STAFF");
        UUID id = generation.request(GenerationKind.IELTS_FULL_TEST, null).id();
        SignedIn.out();
        runUntil(id, "assemble");
        String built = client.assemble("{}");
        artifacts.assemble(id, built);
        var first = generation.detail(id);
        artifacts.assemble(id, built);
        var repeated = generation.detail(id);
        handler.onGaveUp(id, "{\"step\":\"assemble\",\"attempt\":0}", "AI_JOB_STALLED");
        assertThat(generation.detail(id).status()).isEqualTo(GenerationStatus.RUNNING);
        assertThat(repeated.examId()).isEqualTo(first.examId());
        assertThat(repeated.taskIds()).containsExactlyElementsOf(first.taskIds());
        assertThat(jdbc.sql("select count(*) from exams where created_by_user_id=:u").param("u", staff)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from assessment_tasks where created_by_user_id=:u").param("u", staff)
                .query(Long.class).single()).isEqualTo(5);
    }

    @Test
    void invalidAssemblyRollsBackEveryDraftAndCanBeRetried() throws Exception {
        SignedIn.as(jdbc, staff, "STAFF");
        UUID id = generation.request(GenerationKind.IELTS_FULL_TEST, null).id();
        SignedIn.out();
        runUntil(id, "assemble");
        String good = client.assemble("{}");
        var broken = mapper.readTree(good);
        ((ObjectNode) broken.path("tasks").get(1)).put("title", "");
        assertThatThrownBy(() -> artifacts.assemble(id, broken.toString()))
                .isInstanceOf(com.englow3.shared.error.BadRequestException.class);
        assertThat(generation.detail(id).step()).isEqualTo("assemble");
        assertThat(jdbc.sql("select count(*) from exams where created_by_user_id=:u").param("u", staff)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from assessment_tasks where created_by_user_id=:u").param("u", staff)
                .query(Long.class).single()).isZero();
        artifacts.assemble(id, good);
        assertThat(generation.detail(id).taskIds()).hasSize(5);
    }

    @Test
    void finishingRollsBackPartialSubmissionAndRepeatsSafely() {
        SignedIn.as(jdbc, staff, "STAFF");
        UUID id = generation.request(GenerationKind.IELTS_FULL_TEST, null).id();
        SignedIn.out();
        runUntil(id, "finish");
        var pending = generation.detail(id);
        UUID conflicting = pending.taskIds().get(2);
        jdbc.sql("update assessment_tasks set status='PENDING_REVIEW' where id=:id").param("id", conflicting).update();
        assertThatThrownBy(() -> artifacts.finish(id)).isInstanceOf(ConflictException.class);
        assertThat(status("exams", pending.examId())).isEqualTo("DRAFT");
        assertThat(status("assessment_tasks", pending.taskIds().getFirst())).isEqualTo("DRAFT");
        assertThat(generation.detail(id).step()).isEqualTo("finish");
        jdbc.sql("update assessment_tasks set status='DRAFT' where id=:id").param("id", conflicting).update();
        artifacts.finish(id);
        var done = generation.detail(id);
        artifacts.finish(id);
        assertThat(generation.detail(id).fullTestId()).isEqualTo(done.fullTestId());
        assertThat(done.status()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(jdbc.sql("select count(*) from full_tests where created_by_user_id=:u").param("u", staff)
                .query(Long.class).single()).isEqualTo(1);
    }

    private void insertRequest(String status) {
        jdbc.sql("""
                insert into generation_requests (id, kind, requested_by_user_id, status, step, created_at, updated_at)
                values (:id, 'IELTS_FULL_TEST', :u, :status, 'plan', :now, :now)
                """).param("id", UUID.randomUUID()).param("u", staff).param("status", status)
                .param("now", java.sql.Timestamp.from(Instant.now())).update();
    }

    private String status(String table, UUID id) {
        return column("select status from " + table + " where id = :id", id);
    }

    private String column(String sql, UUID id) {
        return jdbc.sql(sql).param("id", id).query(String.class).single();
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
