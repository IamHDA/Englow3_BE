package com.englow3.exam.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.englow3.assessment.controller.AssessmentAuthoringController;
import com.englow3.assessment.dto.request.AssessmentTaskRequest;
import com.englow3.exam.dto.command.ApproveExamCommand;
import com.englow3.exam.dto.command.SaveExamDraftCommand;
import com.englow3.exam.dto.command.SaveFullTestCommand;
import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.command.SubmitExamForReviewCommand;
import com.englow3.exam.dto.request.SaveAuthoringRequest;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTestSittingStatus;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.service.ExamAttemptService;
import com.englow3.exam.service.ExamReviewService;
import com.englow3.exam.service.FullTestService;
import com.englow3.exam.service.FullTestSittingService;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.Validator;

/**
 * The first IELTS trial paper (src/test/resources/ielts), taken through the whole system the way people will: saved
 * through the same authoring endpoint and JSON the admin editor sends, opened and saved again with nothing lost,
 * reviewed and published, sat in full by an IELTS learner who gets some answers wrong on purpose, and put into a full
 * test with five Writing and Speaking tasks - Task 1 with its chart - whose grades give the overall band.
 * <p>
 * The answers come from the paper's own key, so a change to the paper that leaves a question unanswerable fails here.
 */
class IeltsTrialPaperIntegrationTest extends PostgresIntegrationTest {

    private static final String PAPER = "ielts/academic-trial-paper-1.json";
    private static final String TASKS = "ielts/academic-trial-tasks.json";
    private static final String CHART = "ielts/academic-trial-writing-task-1-chart.png";

    @MockitoBean
    private ObjectStorageClient storage;

    @Autowired
    private AdminExamController exams;

    @Autowired
    private AssessmentAuthoringController tasks;

    @Autowired
    private ExamReviewService reviews;

    @Autowired
    private ExamAttemptService attempts;

    @Autowired
    private FullTestService fullTests;

    @Autowired
    private FullTestSittingService sittings;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private Validator validator;

    @Autowired
    private JdbcClient jdbc;

    private UUID author;
    private UUID admin;
    private UUID learner;

    @BeforeEach
    void anAuthorAnAdministratorAndAnIeltsLearner() throws Exception {
        org.mockito.Mockito
                .when(storage.presignGet(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.net.URI.create("https://storage.test/signed").toURL());
        LearnerFixture people = new LearnerFixture(jdbc);
        author = people.learner();
        admin = people.learner();
        learner = people.learner();
        jdbc.sql("update users set role = 'STAFF' where id = :id").param("id", author).update();
        jdbc.sql("update users set role = 'ADMIN' where id = :id").param("id", admin).update();
        jdbc.sql("update users set onboarding_step = 'CURRENT_LEVEL' where id = :id").param("id", learner).update();
        jdbc.sql("""
                insert into user_learning_purposes (user_id, learning_purpose_id)
                select :id, id from learning_purposes where purpose_code = 'CERTIFICATE'
                """).param("id", learner).update();
        jdbc.sql("""
                insert into learner_profiles (id, user_id, target_certificate_type)
                values (:id, :user, 'IELTS')
                """).param("id", UUID.randomUUID()).param("user", learner).update();
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void theTrialPaperGoesFromTheEditorToAnOverallBand() throws IOException {
        // 1. The editor saves the paper, opens it again and saves it unchanged: nothing is lost on the way.
        SignedIn.as(jdbc, author, "STAFF");
        SaveAuthoringRequest request = mapper.readValue(read(PAPER), SaveAuthoringRequest.class);
        assertThat(validator.validate(request)).isEmpty();
        var created = exams.createAuthoring(request).getBody();
        UUID examId = created.id();

        JsonNode firstSave = withoutIds(mapper.valueToTree(exams.authoring(examId).getBody().content()));
        var reopened = exams.authoring(examId).getBody();
        SaveAuthoringRequest unchanged = mapper.convertValue(
                Map.of("version", reopened.version(), "metadata", reopened.metadata(), "content", reopened.content()),
                SaveAuthoringRequest.class);
        exams.updateAuthoring(examId, unchanged);
        assertThat(withoutIds(mapper.valueToTree(exams.authoring(examId).getBody().content()))).isEqualTo(firstSave);
        assertThat(count("question_accepted_answers a join questions q on q.id = a.question_id", examId))
                .isEqualTo(count(firstSave, "acceptedAnswers"));
        assertThat(count("question_set_options o join questions q on q.question_set_id = o.question_set_id", examId))
                .isPositive();

        // 1b. Listening audio, the way scripts/load_ielts_trial.py adds it: upload one file per part to the
        // draft, then save the paper again with each part's key and the version the draft is at.
        ObjectNode withAudio = (ObjectNode) mapper.readTree(read(PAPER));
        JsonNode listeningParts = withAudio.path("content").path("sections").get(0).path("parts");
        for (JsonNode part : listeningParts) {
            byte[] mp3 = { (byte) 0xFF, (byte) 0xF3, 0x44, (byte) 0xC4, 0, 0, 0, 0 };
            String key = exams.uploadMedia(examId, new MockMultipartFile("file", "part.mp3", "audio/mpeg", mp3))
                    .getBody().objectKey();
            ((ObjectNode) part).put("audioObjectKey", key);
        }
        withAudio.put("version", exams.authoring(examId).getBody().version());
        exams.updateAuthoring(examId, mapper.treeToValue(withAudio, SaveAuthoringRequest.class));
        assertThat(exams.authoring(examId).getBody().content().sections().get(0).parts())
                .allSatisfy(part -> assertThat(part.audioObjectKey()).startsWith(examId + "/"));

        // 2. Reviewed and published like any paper.
        reviews.submitForReview(new SubmitExamForReviewCommand(examId));
        SignedIn.as(jdbc, admin, "ADMIN");
        assertThat(reviews.approve(new ApproveExamCommand(examId)).status()).isEqualTo(ExamStatus.PUBLISHED);

        // 3. Five Writing and Speaking tasks; Task 1 gets its chart before review.
        List<UUID> taskIds = new ArrayList<>();
        for (JsonNode task : mapper.readTree(read(TASKS))) {
            ObjectNode body = (ObjectNode) task.deepCopy();
            String chart = body.has("chart") ? body.remove("chart").asText() : null;
            UUID id = tasks.create(mapper.treeToValue(body, AssessmentTaskRequest.class)).getBody().id();
            if (chart != null) {
                byte[] png = read(CHART);
                assertThat(tasks.attachImage(id, new MockMultipartFile("file", chart, "image/png", png)).getBody()
                        .version()).isPositive();
            }
            tasks.submit(id);
            tasks.approve(id);
            taskIds.add(id);
        }

        // 4. The full test: Academic, so Task 1 must carry its chart - which the review checks.
        var fullTest = fullTests.save(new SaveFullTestCommand(null, "IELTS Academic Trial Full Test 1",
                "Trial run of the IELTS flow", CertificateVariant.ACADEMIC, examId, taskIds.get(0), taskIds.get(1),
                taskIds.get(2), taskIds.get(3), taskIds.get(4)));
        fullTests.submitForReview(fullTest.id());
        assertThat(fullTests.approve(fullTest.id()).status()).isEqualTo(ExamStatus.PUBLISHED);

        // 5. The learner starts the full test from onboarding and sits the paper in full.
        SignedIn.as(jdbc, learner);
        var sitting = sittings.start(fullTest.id());
        var attempt = attempts.start(StartExamAttemptCommand.full(examId));
        LearnerExamPaperResult paper = attempts.paperForAttempt(attempt.id());
        List<Item> items = items(paper);

        assertThat(items).hasSize(77);
        assertThat(paper.sections().get(0).parts()).allSatisfy(part -> assertThat(part.audioUrl()).isNotBlank());
        assertThat(numbers(items, SectionType.LISTENING)).startsWith("1", "2").contains("21-22", "23-24")
                .endsWith("40");
        assertThat(numbers(items, SectionType.READING)).startsWith("1").contains("24-25").endsWith("40");

        // A draft keeps what was typed.
        SubmittedAnswer typed = new SubmittedAnswer(items.get(0).question().id(), List.of(), "Kowalski");
        assertThat(attempts.saveDraft(new SaveExamDraftCommand(attempt.id(), 0, List.of(typed))).answers())
                .extracting(SubmittedAnswer::textAnswer).containsExactly("Kowalski");

        Map<String, BigDecimal> expected = new HashMap<>();
        List<SubmittedAnswer> answers = new ArrayList<>();
        for (Item item : items) {
            SubmittedAnswer answer = answer(item);
            if (answer != null) {
                answers.add(answer);
            }
        }
        ExamAttemptResult result = attempts.submit(new SubmitExamAttemptCommand(attempt.id(), answers));

        // Listening: 10 marks lost on purpose -> 30/40 = 7.0. Reading: 6 lost -> 34/40 = 7.5.
        expected.put("LISTENING", new BigDecimal("30"));
        expected.put("READING", new BigDecimal("34"));
        for (var section : result.sections()) {
            assertThat(section.rawScore()).as(section.sectionType().name())
                    .isEqualByComparingTo(expected.get(section.sectionType().name()));
        }
        assertThat(band(result, SectionType.LISTENING)).isEqualByComparingTo("7.0");
        assertThat(band(result, SectionType.READING)).isEqualByComparingTo("7.5");

        // The review shows what was typed next to what was accepted, and half marks on a choose-two.
        ExamAttemptResult review = attempts.result(attempt.id());
        var surname = review.questions().stream().filter(q -> q.questionId().equals(items.get(0).question().id()))
                .findFirst().orElseThrow();
        assertThat(surname.textAnswer()).isEqualTo("Kowalsky");
        assertThat(surname.acceptedAnswers()).containsExactly("Kowalski");
        var chooseTwo = review.questions().stream().filter(q -> q.numberFrom() == 21 && q.numberTo() == 22).findFirst()
                .orElseThrow();
        assertThat(chooseTwo.awardedRawScore()).isEqualByComparingTo("1");

        // 6. Provisional band, the learner's level, onboarding moved on.
        var provisional = sittings.view(sitting.id());
        assertThat(provisional.provisionalBand()).isEqualByComparingTo("7.0");
        assertThat(jdbc.sql("select onboarding_step from users where id = :u").param("u", learner).query(String.class)
                .single()).isEqualTo("LEARNING_GOAL");

        // 7. Writing 6.5 + 6.5 (Task 2 counts double) and Speaking 6.0 x 3: mean 6.75 rounds up to 7.0.
        graded(taskIds.get(0), "6.5");
        graded(taskIds.get(1), "6.5");
        graded(taskIds.get(2), "6.0");
        graded(taskIds.get(3), "6.0");
        graded(taskIds.get(4), "6.0");
        var done = sittings.view(sitting.id());
        assertThat(done.status()).isEqualTo(FullTestSittingStatus.COMPLETED);
        assertThat(done.writingBand()).isEqualByComparingTo("6.5");
        assertThat(done.speakingBand()).isEqualByComparingTo("6.0");
        assertThat(done.overallBand()).isEqualByComparingTo("7.0");
    }

    /**
     * A set written by data_pipeline/generators/generate_ielts_set.py (made with its stand-in model, so the content is
     * filler but the shape is exactly what the pipeline builds) goes in the way load_ielts_trial.py --set-dir loads it,
     * passes review, makes a full test, and gives 40/40 and band 9.0 when every answer is right.
     */
    @Test
    void anAiWrittenSetLoadsPassesReviewAndCanBeSat() throws IOException {
        String set = "ielts/ai-sample-set/";
        SignedIn.as(jdbc, author, "STAFF");
        SaveAuthoringRequest request = mapper.readValue(read(set + "paper.json"), SaveAuthoringRequest.class);
        assertThat(validator.validate(request)).isEmpty();
        UUID examId = exams.createAuthoring(request).getBody().id();
        reviews.submitForReview(new SubmitExamForReviewCommand(examId));

        SignedIn.as(jdbc, admin, "ADMIN");
        assertThat(reviews.approve(new ApproveExamCommand(examId)).status()).isEqualTo(ExamStatus.PUBLISHED);
        List<UUID> taskIds = new ArrayList<>();
        for (JsonNode task : mapper.readTree(read(set + "tasks.json"))) {
            ObjectNode body = (ObjectNode) task.deepCopy();
            String chart = body.has("chart") ? body.remove("chart").asText() : null;
            AssessmentTaskRequest taskRequest = mapper.treeToValue(body, AssessmentTaskRequest.class);
            assertThat(validator.validate(taskRequest)).isEmpty();
            UUID id = tasks.create(taskRequest).getBody().id();
            if (chart != null) {
                tasks.attachImage(id, new MockMultipartFile("file", chart, "image/png", read(set + chart)));
            }
            tasks.submit(id);
            tasks.approve(id);
            taskIds.add(id);
        }
        var fullTest = fullTests
                .save(new SaveFullTestCommand(null, "IELTS Academic AI Set", "", CertificateVariant.ACADEMIC, examId,
                        taskIds.get(0), taskIds.get(1), taskIds.get(2), taskIds.get(3), taskIds.get(4)));
        fullTests.submitForReview(fullTest.id());
        assertThat(fullTests.approve(fullTest.id()).status()).isEqualTo(ExamStatus.PUBLISHED);

        SignedIn.as(jdbc, learner);
        sittings.start(fullTest.id());
        var attempt = attempts.start(StartExamAttemptCommand.full(examId));
        List<Item> items = items(attempts.paperForAttempt(attempt.id()), set + "paper.json");
        assertThat(numbers(items, SectionType.LISTENING)).startsWith("1").endsWith("40");
        assertThat(numbers(items, SectionType.READING)).startsWith("1").endsWith("40");
        ExamAttemptResult result = attempts.submit(new SubmitExamAttemptCommand(attempt.id(),
                items.stream().map(IeltsTrialPaperIntegrationTest::rightAnswer).toList()));
        assertThat(band(result, SectionType.LISTENING)).isEqualByComparingTo("9.0");
        assertThat(band(result, SectionType.READING)).isEqualByComparingTo("9.0");
    }

    /** A question as the learner sees it, with the key the paper file gives it. */
    private record Item(SectionType section, LearnerExamPaperResult.QuestionSetResult set,
            LearnerExamPaperResult.QuestionResult question, JsonNode key) {
    }

    private List<Item> items(LearnerExamPaperResult paper) throws IOException {
        return items(paper, PAPER);
    }

    private List<Item> items(LearnerExamPaperResult paper, String paperFile) throws IOException {
        List<JsonNode> keys = new ArrayList<>();
        for (JsonNode section : mapper.readTree(read(paperFile)).path("content").path("sections")) {
            for (JsonNode part : section.path("parts")) {
                for (JsonNode set : part.path("questionSets")) {
                    set.path("questions").forEach(keys::add);
                }
            }
        }
        List<Item> items = new ArrayList<>();
        for (var section : paper.sections()) {
            for (var part : section.parts()) {
                for (var set : part.questionSets()) {
                    for (var question : set.questions()) {
                        JsonNode key = keys.get(items.size());
                        assertThat(question.content()).isEqualTo(key.path("content").asText());
                        items.add(new Item(section.sectionType(), set, question, key));
                    }
                }
            }
        }
        return items;
    }

    private static List<String> numbers(List<Item> items, SectionType section) {
        return items.stream().filter(item -> item.section() == section)
                .map(item -> item.question().numberFrom() == item.question().numberTo()
                        ? String.valueOf(item.question().numberFrom())
                        : item.question().numberFrom() + "-" + item.question().numberTo())
                .toList();
    }

    /**
     * The right answer from the key, except where this run gets it wrong on purpose. Listening loses 10: a misspelt
     * surname (1), a skipped phone number (1), one wrong letter of 21-22 (1), two wrong letters for 23-24 (2), a wrong
     * opinion for 25 (1), four wrong notes 31-34 (4). Reading loses 6: question 1 (1), heading 14 (1), 24-25 skipped
     * (2), sentences 37-38 (2). Some right answers are typed oddly to check case and spaces are forgiven.
     */
    private SubmittedAnswer answer(Item item) {
        var q = item.question();
        int number = q.numberFrom();
        boolean listening = item.section() == SectionType.LISTENING;
        if (listening && number == 2 || !listening && number == 24) {
            return null;
        }
        if (q.questionType() == QuestionType.TEXT_ENTRY) {
            String right = item.key().path("acceptedAnswers").get(0).path("text").asText();
            String text = listening && number == 1 ? "Kowalsky"
                    : listening && number >= 31 && number <= 34 || !listening && (number == 37 || number == 38)
                            ? "wrong"
                            : listening && number == 3 ? "  POTTERY " : right;
            return new SubmittedAnswer(q.id(), List.of(), text);
        }
        if (q.questionType() == QuestionType.MATCHING) {
            String right = item.key().path("matchingLabels").get(0).asText();
            String label = listening && number == 25 || !listening && number == 14 ? item.set().setOptions().stream()
                    .map(o -> o.label()).filter(l -> !l.equals(right)).findFirst().orElseThrow() : right;
            return new SubmittedAnswer(q.id(), List.of(item.set().setOptions().stream()
                    .filter(o -> o.label().equals(label)).findFirst().orElseThrow().id()), null);
        }
        List<UUID> correct = new ArrayList<>();
        List<UUID> wrong = new ArrayList<>();
        JsonNode keyOptions = item.key().path("options");
        for (int i = 0; i < q.options().size(); i++) {
            (keyOptions.get(i).path("correct").asBoolean() ? correct : wrong).add(q.options().get(i).id());
        }
        List<UUID> chosen = listening && number == 21 ? List.of(correct.get(0), wrong.get(0))
                : listening && number == 23 ? List.of(wrong.get(0), wrong.get(1))
                        : !listening && number == 1 ? List.of(wrong.get(0)) : correct;
        return new SubmittedAnswer(q.id(), chosen, null);
    }

    /** The answer the key gives, with nothing wrong on purpose. */
    private static SubmittedAnswer rightAnswer(Item item) {
        var q = item.question();
        if (q.questionType() == QuestionType.TEXT_ENTRY) {
            return new SubmittedAnswer(q.id(), List.of(),
                    item.key().path("acceptedAnswers").get(0).path("text").asText());
        }
        if (q.questionType() == QuestionType.MATCHING) {
            String label = item.key().path("matchingLabels").get(0).asText();
            return new SubmittedAnswer(q.id(), List.of(item.set().setOptions().stream()
                    .filter(o -> o.label().equals(label)).findFirst().orElseThrow().id()), null);
        }
        List<UUID> correct = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            if (item.key().path("options").get(i).path("correct").asBoolean()) {
                correct.add(q.options().get(i).id());
            }
        }
        return new SubmittedAnswer(q.id(), correct, null);
    }

    private static BigDecimal band(ExamAttemptResult result, SectionType section) {
        return result.sections().stream().filter(s -> s.sectionType() == section).findFirst().orElseThrow().band();
    }

    private void graded(UUID task, String band) {
        String skill = jdbc.sql("select skill from assessment_tasks where id = :id").param("id", task)
                .query(String.class).single();
        jdbc.sql("""
                insert into assessment_attempts (id, user_id, task_id, client_key, skill, task_snapshot, answer_text,
                                                 audio_object_key, status, report, created_at, submitted_at,
                                                 assessed_at)
                values (:id, :user, :task, :key, :skill, '{}'::jsonb, 'An answer', :audio, 'COMPLETED',
                        cast(:report as jsonb), now(), now(), now())
                """).param("id", UUID.randomUUID()).param("user", learner).param("task", task)
                .param("key", UUID.randomUUID()).param("skill", skill)
                .param("audio", "SPEAKING".equals(skill) ? "recordings/" + UUID.randomUUID() + ".wav" : null)
                .param("report", "{\"overall\": " + band + "}").update();
    }

    private long count(String joined, UUID examId) {
        return jdbc.sql("""
                select count(*) from %s
                  join question_sets qs on qs.id = q.question_set_id
                  join section_parts p on p.id = qs.section_part_id
                  join exam_sections s on s.id = p.exam_section_id
                 where s.exam_id = :exam
                """.formatted(joined)).param("exam", examId).query(Long.class).single();
    }

    private static long count(JsonNode tree, String field) {
        long found = tree.has(field) ? tree.path(field).size() : 0;
        for (JsonNode child : tree) {
            found += child.isContainerNode() ? count(child, field) : 0;
        }
        return found;
    }

    /** Ids change on every save - the tree is replaced - so a round trip is compared without them. */
    private static JsonNode withoutIds(JsonNode tree) {
        if (tree.isObject()) {
            ((ObjectNode) tree).remove(Set.of("id"));
        }
        tree.forEach(IeltsTrialPaperIntegrationTest::withoutIds);
        return tree;
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
