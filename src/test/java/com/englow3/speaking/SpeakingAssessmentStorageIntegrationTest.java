package com.englow3.speaking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingPrompt;
import com.englow3.speaking.repository.SpeakingAttemptRepository;
import com.englow3.speaking.repository.SpeakingAttemptWordRepository;
import com.englow3.speaking.repository.SpeakingPromptRepository;
import com.englow3.speaking.service.SpeakingAssessmentWriter;
import com.englow3.speaking.service.SpeechAssessmentParser;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The far end of a speaking assessment: what the provider said, written where the learner will read it.
 * <p>
 * The provider call itself needs a key and object storage, and neither exists here; the writing does not, and it is the
 * half that had never touched a real database. The word breakdown lives in a {@code jsonb} column that could not be
 * written at all until it was mapped as JSON, so every assessment would have failed at the last step.
 */
class SpeakingAssessmentStorageIntegrationTest extends PostgresIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PROVIDER_ANSWER = """
            {"recognized_text":"Please sit on this seat.","accuracy":81.5,"fluency":90,"completeness":100,
             "prosody":72.25,"pronunciation":84,
             "words":[{"word":"please","accuracy":95,"error_type":"None","offset_ms":0,"duration_ms":300,
                       "phonemes":[{"phoneme":"p","accuracy":97},{"phoneme":"l","accuracy":93}]},
                      {"word":"sit","accuracy":61,"error_type":"Mispronunciation","offset_ms":320,"duration_ms":260,
                       "phonemes":[{"phoneme":"ɪ","accuracy":48}]}]}
            """;

    @Autowired
    private SpeakingAssessmentWriter writer;

    @Autowired
    private SpeakingPromptRepository promptRepo;

    @Autowired
    private SpeakingAttemptRepository attemptRepo;

    @Autowired
    private SpeakingAttemptWordRepository wordRepo;

    @Autowired
    private JdbcClient jdbc;

    private UUID attemptId;

    @BeforeEach
    void setUp() {
        UUID learner = new LearnerFixture(jdbc).learner();
        SpeakingPrompt prompt = promptRepo.saveAndFlush(SpeakingPrompt.draft("seat-" + UUID.randomUUID(), "Seat vs sit",
                "Minimal Pairs", "A2", "Please sit on this seat.", null, null, null, "[]", learner));

        SpeakingAttempt attempt = SpeakingAttempt.awaitingUpload(learner, prompt.getId(), "speaking/a.wav",
                "audio/wav");
        attempt.markQueued();
        attemptId = attemptRepo.saveAndFlush(attempt).getId();
    }

    @Test
    void storesTheScoresAndMarksTheAttemptAssessed() {
        writer.storeAssessment(attemptId, SpeechAssessmentParser.parse(MAPPER, PROVIDER_ANSWER));

        SpeakingAttempt stored = attemptRepo.findById(attemptId).orElseThrow();
        assertThat(stored.getStatus().name()).isEqualTo("ASSESSED");
        assertThat(stored.getRecognizedText()).isEqualTo("Please sit on this seat.");
        assertThat(stored.getAccuracyPercent()).isEqualByComparingTo("81.5");
        assertThat(stored.getProsodyPercent()).isEqualByComparingTo("72.25");
    }

    /** The per-word breakdown, phonemes included - the column that could not be written before. */
    @Test
    void storesEachWordWithItsPhonemes() {
        writer.storeAssessment(attemptId, SpeechAssessmentParser.parse(MAPPER, PROVIDER_ANSWER));

        var words = wordRepo.findBySpeakingAttemptIdOrderByOrderNo(attemptId);
        assertThat(words).extracting(word -> word.getWord()).containsExactly("please", "sit");
        assertThat(words.get(1).getErrorType()).isEqualTo("Mispronunciation");
        assertThat(words.get(1).getPhonemes()).contains("ɪ");
    }

    /**
     * A job can run twice - a stall reclaim is exactly that - and the second run must replace the word list rather than
     * append a second copy of it.
     */
    @Test
    void replacesTheWordsRatherThanDoublingThemOnARerun() {
        var assessment = SpeechAssessmentParser.parse(MAPPER, PROVIDER_ANSWER);
        writer.storeAssessment(attemptId, assessment);

        jdbc.sql("update speaking_attempts set status = 'QUEUED' where id = :id").param("id", attemptId).update();
        writer.storeAssessment(attemptId, assessment);

        assertThat(wordRepo.findBySpeakingAttemptIdOrderByOrderNo(attemptId)).hasSize(2);
    }

    @Test
    void recordsWhyAnAssessmentFailed() {
        writer.markFailed(attemptId, "SPEECH_ASSESSMENT_REJECTED");

        SpeakingAttempt stored = attemptRepo.findById(attemptId).orElseThrow();
        assertThat(stored.getStatus().name()).isEqualTo("FAILED");
        assertThat(stored.getErrorCode()).isEqualTo("SPEECH_ASSESSMENT_REJECTED");
    }
}
