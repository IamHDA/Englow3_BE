package com.englow3.speaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.speaking.service.SpeechAssessmentParser.Assessment;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpeechAssessmentParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Assessment parse(String json) {
        return SpeechAssessmentParser.parse(mapper, json);
    }

    @Test
    void readsEveryScoreAndTheWordsBehindThem() {
        Assessment assessment = parse("""
                {
                  "provider": "azure",
                  "recognized_text": "She has been there",
                  "accuracy": 92.5,
                  "fluency": 88,
                  "completeness": 100,
                  "prosody": 79.25,
                  "pronunciation": 90,
                  "words": [
                    {
                      "word": "She", "accuracy": 98, "error_type": "None",
                      "offset_ms": 120, "duration_ms": 220,
                      "phonemes": [{"phoneme": "ʃ", "accuracy": 99}, {"phoneme": "iː", "accuracy": 97}]
                    },
                    {"word": "has", "accuracy": 61, "error_type": "Mispronunciation"}
                  ]
                }
                """);

        assertThat(assessment.recognizedText()).isEqualTo("She has been there");
        assertThat(assessment.accuracy()).isEqualByComparingTo("92.5");
        assertThat(assessment.prosody()).isEqualByComparingTo("79.25");
        assertThat(assessment.words()).hasSize(2);
        assertThat(assessment.words().get(0).phonemes()).extracting(SpeechAssessmentParser.Phoneme::phoneme)
                .containsExactly("ʃ", "iː");
        assertThat(assessment.words().get(1).errorType()).isEqualTo("Mispronunciation");
    }

    /**
     * The decision this class exists for. Every score in the adapter's schema is optional because the provider omits
     * them, and reading a missing one as zero would tell a learner they scored nothing when nothing was measured.
     */
    @Test
    void leavesAnUnmeasuredScoreMissingRatherThanZero() {
        Assessment assessment = parse("""
                {"recognized_text": "Hello", "accuracy": 80}
                """);

        assertThat(assessment.accuracy()).isEqualByComparingTo("80");
        assertThat(assessment.prosody()).isNull();
        assertThat(assessment.fluency()).isNull();
        assertThat(assessment.completeness()).isNull();
        assertThat(assessment.pronunciation()).isNull();
    }

    @Test
    void treatsAnExplicitJsonNullAsUnmeasured() {
        Assessment assessment = parse("""
                {"recognized_text": "Hello", "prosody": null}
                """);

        assertThat(assessment.prosody()).isNull();
    }

    @Test
    void copesWithAnAssessmentThatCarriesNoWordBreakdown() {
        Assessment assessment = parse("""
                {"recognized_text": "Hello", "accuracy": 80}
                """);

        assertThat(assessment.words()).isEmpty();
    }

    /** One malformed element must not throw away an otherwise good assessment. */
    @Test
    void skipsAWordEntryWithNoWordInIt() {
        Assessment assessment = parse("""
                {
                  "recognized_text": "Hello there",
                  "words": [{"accuracy": 90}, {"word": "there", "accuracy": 85}]
                }
                """);

        assertThat(assessment.words()).extracting(SpeechAssessmentParser.Word::word).containsExactly("there");
    }

    @Test
    void keepsTheScaleTheProviderSentRatherThanRounding() {
        Assessment assessment = parse("""
                {"recognized_text": "Hello", "accuracy": 92.53}
                """);

        assertThat(assessment.accuracy()).isEqualTo(new BigDecimal("92.53"));
    }

    /** An assessment that recognised nothing is not an assessment, and a row of nulls would pretend otherwise. */
    @Test
    void refusesAnAssessmentThatRecognisedNoSpeech() {
        assertThatThrownBy(() -> parse("""
                {"recognized_text": "  ", "accuracy": 0}
                """)).isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                .isEqualTo("SPEECH_ASSESSMENT_EMPTY");
    }

    @Test
    void refusesABodyThatIsNotJsonAtAll() {
        assertThatThrownBy(() -> parse("<html>502 Bad Gateway</html>")).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("SPEECH_ASSESSMENT_UNREADABLE");
    }

    /** A score arriving as a string is the provider breaking its own contract - not a number, so not measured. */
    @Test
    void ignoresAScoreThatIsNotANumber() {
        Assessment assessment = parse("""
                {"recognized_text": "Hello", "accuracy": "high"}
                """);

        assertThat(assessment.accuracy()).isNull();
    }
}
