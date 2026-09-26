package com.englow3.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.ai.entity.AiJob;
import com.englow3.ai.repository.AiJobRepository;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.ai.service.AiJobWorkerQueue;
import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingAttemptWord;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingAttemptWordRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;

/**
 * Every {@code jsonb} column an entity maps, written through JPA and read back.
 * <p>
 * All four were mapped as a plain {@code String} with {@code columnDefinition = "jsonb"}. That attribute only shapes
 * generated DDL; it says nothing about binding, so Hibernate sent each value as {@code varchar} and PostgreSQL refused
 * it. The consequence was that no speaking submission and no tutor question could ever be queued, no admin could create
 * a speaking prompt, and no assessment could be stored. None of it showed, because every test that touched these
 * entities either mocked the repository or built the entity without saving it.
 * <p>
 * The rule this holds: a {@code jsonb} column mapped to a {@code String} needs {@code @JdbcTypeCode(SqlTypes.JSON)}. A
 * new one added without it fails here rather than on the first request.
 */
class JsonColumnsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AiJobQueue queue;

    @Autowired
    private AiJobWorkerQueue workerQueue;

    @Autowired
    private AiJobRepository jobRepo;

    @Autowired
    private SpeakingPromptRepository promptRepo;

    @Autowired
    private SpeakingAttemptRepository attemptRepo;

    @Autowired
    private SpeakingAttemptWordRepository wordRepo;

    @Autowired
    private JdbcClient jdbc;

    /** The write every speaking submission and every tutor question makes first. */
    @Test
    void queuesAJobWithItsPayload() {
        UUID learner = new LearnerFixture(jdbc).learner();
        UUID target = UUID.randomUUID();

        queue.enqueueTutorReply(target, "{\"question\":\"What is a gerund?\"}", "tutor:" + target, "v1", learner);
        AiJob job = jobRepo.findByIdempotencyKey("tutor:" + target).orElseThrow();

        assertThat(jobRepo.findById(job.getId())).get().extracting(AiJob::getInputPayload).asString()
                .contains("gerund");
    }

    /** The write that records what the provider said. */
    @Test
    void recordsTheOutputOfAFinishedJob() {
        UUID learner = new LearnerFixture(jdbc).learner();
        UUID target = UUID.randomUUID();
        queue.enqueueTutorReply(target, "{}", "tutor:" + target, "v1", learner);
        AiJob job = jobRepo.findByIdempotencyKey("tutor:" + target).orElseThrow();
        workerQueue.claimBatch(50);

        workerQueue.record(job.getId(), AiJobHandler.Outcome.succeeded("{\"content\":\"A verb used as a noun.\"}"));

        assertThat(jobRepo.findById(job.getId())).get().extracting(AiJob::getOutputPayload).asString()
                .contains("A verb used as a noun.");
    }

    /** An administrator creating a prompt. The tips are what make this column exist. */
    @Test
    void storesAPromptsTips() {
        UUID author = new LearnerFixture(jdbc).learner();

        SpeakingPrompt prompt = promptRepo.saveAndFlush(SpeakingPrompt.draft("seat-" + UUID.randomUUID(), "Seat vs sit",
                "Minimal Pairs", "A2", "Please sit on this seat.", null, null, "/iː/ vs /ɪ/",
                "[\"Smile for /iː/\",\"Relax for /ɪ/\"]", author));

        assertThat(promptRepo.findById(prompt.getId())).get().extracting(SpeakingPrompt::getTips).asString()
                .contains("Smile for /iː/");
    }

    /** The per-word breakdown an assessment writes, phonemes and all. */
    @Test
    void storesAnAssessmentsPhonemes() {
        UUID learner = new LearnerFixture(jdbc).learner();
        SpeakingPrompt prompt = promptRepo.saveAndFlush(SpeakingPrompt.draft("sit-" + UUID.randomUUID(), "Sit",
                "Minimal Pairs", "A2", "Please sit.", null, null, null, "[]", learner));
        SpeakingAttempt attempt = attemptRepo
                .saveAndFlush(SpeakingAttempt.awaitingUpload(learner, prompt.getId(), "speaking/a.wav", "audio/wav"));

        SpeakingAttemptWord word = wordRepo.saveAndFlush(SpeakingAttemptWord.of(attempt.getId(), 1, "sit",
                new BigDecimal("72.50"), "Mispronunciation", 120, 340, "[{\"phoneme\":\"ɪ\",\"accuracy\":61.0}]"));

        assertThat(wordRepo.findById(word.getId())).get().extracting(SpeakingAttemptWord::getPhonemes).asString()
                .contains("\"ɪ\"");
    }

    /**
     * Read back as JSON, not as the text that went in. PostgreSQL normalises jsonb - whitespace goes - which is what
     * proves the value was stored as JSON rather than coerced from a string that happened to look like it.
     */
    @Test
    void storesTheValueAsJsonRatherThanAsText() {
        UUID learner = new LearnerFixture(jdbc).learner();
        UUID target = UUID.randomUUID();
        queue.enqueueSpeechAssessment(target, "{ \"a\" :   1 }", "speech:" + target, "v1", learner);

        String type = jdbc.sql("select jsonb_typeof(input_payload) from ai_jobs where target_id = :target")
                .param("target", target).query(String.class).single();

        assertThat(type).isEqualTo("object");
    }
}
