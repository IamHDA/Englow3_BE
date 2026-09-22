package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reading a generated shadowing batch.
 * <p>
 * The fixtures are the shape {@code data_pipeline/schemas/json/shadowing_batch.schema.json} defines, and
 * {@link TheSchemaContract} holds them to it. The flashcard importer was written against a guessed shape once and its
 * tests agreed with it because both came from the same guess; this is the arrangement that stops that repeating.
 */
class DictationImportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ONE_CLIP = """
            {"batch_metadata":{"batch_id":"b1","module_type":"shadowing","generated_by":"pipeline",
                               "generated_at":"2026-01-01T00:00:00Z","total_records":1},
             "clips":[{"clip_id":"airport-01","cefr_level":"A2","accent":"us",
                       "script":"At the airport.\\nA second line of script.",
                       "audio_url":"shadowing/airport-01.mp3","duration_ms":8400,
                       "segments":[{"order":1,"text":"Where is the gate?","start_ms":0,"end_ms":2100},
                                   {"order":2,"text":"It is over there.","start_ms":2100,"end_ms":4300}]}]}
            """;

    @Nested
    class ReadingAClip {

        /** One lesson per clip, one sentence per segment. */
        @Test
        void turnsAClipIntoALessonWithASentencePerSegment() {
            var report = DictationImport.read(MAPPER, ONE_CLIP);

            assertThat(report.acceptedCount()).isEqualTo(1);
            assertThat(report.sentenceCount()).isEqualTo(2);
            assertThat(report.lessons().get(0).slug()).isEqualTo("airport-01");
            assertThat(report.lessons().get(0).targetLevel()).isEqualTo("A2");
        }

        /**
         * Every sentence points at the same recording and differs only by where it starts. That is the whole reason the
         * offset columns exist - the alternative was cutting one clip into a file per line.
         */
        @Test
        void pointsEverySentenceAtTheSameRecordingWithItsOwnWindow() {
            var lesson = DictationImport.read(MAPPER, ONE_CLIP).lessons().get(0);

            assertThat(lesson.audioObjectKey()).isEqualTo("shadowing/airport-01.mp3");
            assertThat(lesson.segments()).extracting(DictationImport.Segment::startMs).containsExactly(0, 2100);
            assertThat(lesson.segments()).extracting(DictationImport.Segment::endMs).containsExactly(2100, 4300);
        }

        /** A list of "shadow-0043" is a list nobody can read, so the script's first line becomes the title. */
        @Test
        void titlesTheLessonFromTheFirstLineOfTheScript() {
            assertThat(DictationImport.read(MAPPER, ONE_CLIP).lessons().get(0).title()).isEqualTo("At the airport.");
        }

        /** A clip of 8.4 seconds reported as 8 would have the player stop before the last word. */
        @Test
        void roundsTheDurationUpRatherThanDown() {
            assertThat(DictationImport.read(MAPPER, ONE_CLIP).lessons().get(0).durationSeconds()).isEqualTo(9);
            assertThat(DictationImport.ceilSeconds(1)).isEqualTo(1);
            assertThat(DictationImport.ceilSeconds(0)).isZero();
        }
    }

    @Nested
    class Rejecting {

        private static String clipWithout(String field) {
            return ONE_CLIP.replace("\"%s\"".formatted(field), "\"removed_%s\"".formatted(field));
        }

        /** The pipeline leaves audio_url null until the recording exists; that clip is not a lesson yet. */
        @Test
        void refusesAClipWithNoRecordingYet() {
            var report = DictationImport.read(MAPPER, clipWithout("audio_url"));

            assertThat(report.rejections()).singleElement().satisfies(rejection -> {
                assertThat(rejection.clipId()).isEqualTo("airport-01");
                assertThat(rejection.reason()).isEqualTo("No audio");
            });
        }

        @Test
        void refusesAClipWithNothingToTranscribe() {
            var report = DictationImport.read(MAPPER, ONE_CLIP.replace("\"segments\"", "\"removed\""));

            assertThat(report.rejections()).singleElement()
                    .satisfies(rejection -> assertThat(rejection.reason()).isEqualTo("No segments"));
        }

        /**
         * Half a window is not a window - the player would have nowhere to stop - so the segment is dropped while the
         * rest of the clip still imports.
         */
        @Test
        void dropsASegmentWhoseWindowIsIncomplete() {
            var report = DictationImport.read(MAPPER,
                    ONE_CLIP.replace("\"order\":2,\"text\":\"It is over there.\",\"start_ms\":2100,\"end_ms\":4300",
                            "\"order\":2,\"text\":\"It is over there.\",\"start_ms\":2100"));

            assertThat(report.acceptedCount()).isEqualTo(1);
            assertThat(report.sentenceCount()).isEqualTo(1);
        }

        @Test
        void dropsASegmentThatEndsBeforeItStarts() {
            var report = DictationImport.read(MAPPER, ONE_CLIP.replace("\"end_ms\":4300", "\"end_ms\":100"));

            assertThat(report.sentenceCount()).isEqualTo(1);
        }
    }

    @Nested
    class RefusingTheWholeFile {

        @Test
        void refusesSomethingThatIsNotAShadowingBatch() {
            assertThatThrownBy(() -> DictationImport.read(MAPPER, "[{\"clip_id\":\"x\"}]"))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("DICTATION_IMPORT_NOT_A_BATCH");
        }

        @Test
        void refusesSomethingThatIsNotJson() {
            assertThatThrownBy(() -> DictationImport.read(MAPPER, "clip_id,text"))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("DICTATION_IMPORT_UNREADABLE");
        }

        @Test
        void acceptsABatchWithNoClipsAsDoingNothing() {
            assertThat(DictationImport.read(MAPPER, "{\"clips\":[]}").isEmpty()).isTrue();
        }
    }

    /**
     * That the importer reads the shape the generator writes. Read from the schema file, so a rename on the pipeline
     * side fails here naming the field rather than emptying every lesson at import time.
     */
    @Nested
    class TheSchemaContract {

        private static final Path SCHEMA = Path.of("data_pipeline", "schemas", "json", "shadowing_batch.schema.json");

        static boolean schemaIsPresent() {
            return Files.exists(SCHEMA);
        }

        private static JsonNode schema() throws Exception {
            return MAPPER.readTree(Files.readString(SCHEMA));
        }

        @Test
        @EnabledIf("schemaIsPresent")
        void readsAClipByTheFieldsTheSchemaGivesIt() throws Exception {
            JsonNode clip = schema().path("$defs").path("ShadowingClip");

            assertThat(clip.path("properties").fieldNames()).toIterable().contains("clip_id", "cefr_level", "script",
                    "segments", "audio_url", "duration_ms");
            // audio_url is not required, which is why a clip without one is a rejection rather than a broken row.
            assertThat(clip.path("required")).extracting(JsonNode::asText).contains("clip_id", "cefr_level", "script",
                    "segments");
        }

        @Test
        @EnabledIf("schemaIsPresent")
        void readsASegmentByTheFieldsTheSchemaGivesIt() throws Exception {
            JsonNode segment = schema().path("$defs").path("ShadowingSegment");

            assertThat(segment.path("properties").fieldNames()).toIterable().contains("order", "text", "start_ms",
                    "end_ms");
            assertThat(segment.path("required")).extracting(JsonNode::asText).contains("order", "text");
        }

        /** The batch is an object with a clips array, not a bare list - unlike the flashcard batch. */
        @Test
        @EnabledIf("schemaIsPresent")
        void readsTheBatchAsAnObjectWithClips() throws Exception {
            assertThat(schema().path("properties").fieldNames()).toIterable().contains("clips");
        }
    }
}
