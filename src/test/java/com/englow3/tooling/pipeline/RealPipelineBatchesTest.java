package com.englow3.tooling.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.englow3.dictation.helper.DictationImport;
import com.englow3.flashcard.helper.FlashcardImport;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The importers run over the batches the pipeline actually produced, not fixtures written to look like them.
 * <p>
 * Fixtures are written from someone's idea of the format, and twice that idea was wrong in a way the fixtures shared:
 * once about the shape of a card, once about the file around the cards. Both were found only by opening the real
 * output. This does that on purpose, every time someone has the output to hand.
 * <p>
 * The generated data is not in the repository - it is 160 MB of media and lives wherever the pipeline last ran - so
 * this is off unless {@code ENGLOW3_PIPELINE_OUTPUT} points at a pipeline {@code output/} directory.
 */
@EnabledIfEnvironmentVariable(named = "ENGLOW3_PIPELINE_OUTPUT", matches = ".+")
class RealPipelineBatchesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Path output() {
        return Path.of(System.getenv("ENGLOW3_PIPELINE_OUTPUT"));
    }

    private static List<Path> batches(String folder, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(output().resolve(folder))) {
            return files.filter(file -> file.getFileName().toString().startsWith(prefix)).sorted().toList();
        }
    }

    @Test
    void readsEveryGeneratedFlashcard() throws IOException {
        int accepted = 0;
        for (Path batch : batches("flashcards", "flashcard_batch_")) {
            var report = FlashcardImport.read(MAPPER, Files.readString(batch));

            assertThat(report.rejections()).as("rejected rows in %s", batch.getFileName()).isEmpty();
            accepted += report.acceptedCount();
        }

        assertThat(accepted).isPositive();
    }

    /** Every card's audio resolves to a key that names a file the media folder actually holds. */
    @Test
    void pointsEveryCardAtAFileThatExists() throws IOException {
        for (Path batch : batches("flashcards", "flashcard_batch_")) {
            for (var card : FlashcardImport.read(MAPPER, Files.readString(batch)).cards()) {
                for (String key : new String[] { card.audioUsObjectKey(), card.audioUkObjectKey() }) {
                    if (key != null) {
                        assertThat(output().resolve("media").resolve(key)).as("audio for %s", card.lemma()).exists();
                    }
                }
            }
        }
    }

    @Test
    void readsEveryGeneratedShadowingClip() throws IOException {
        for (Path batch : batches("shadowing", "shadowing_batch_")) {
            var report = DictationImport.read(MAPPER, Files.readString(batch));

            assertThat(report.rejections()).as("rejected clips in %s", batch.getFileName()).isEmpty();
            assertThat(report.sentenceCount()).isPositive();
            for (var lesson : report.lessons()) {
                assertThat(output().resolve("media").resolve(lesson.audioObjectKey())).as("audio for %s", lesson.slug())
                        .exists();
            }
        }
    }
}
