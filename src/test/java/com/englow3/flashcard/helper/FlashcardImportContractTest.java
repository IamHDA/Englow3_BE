package com.englow3.flashcard.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * That the importer reads the shape the pipeline actually writes.
 * <p>
 * The two live in one repository and never meet until someone uploads a file, so until now the only thing holding them
 * together was whoever wrote the importer having read the schema correctly. That failed once: the importer was written
 * against a guessed shape - a definition as a plain string, an example as {@code text} - and its own tests agreed with
 * it, because the fixtures came from the same guess. Every generated card would have been rejected as having no
 * definition.
 * <p>
 * This reads the schema file itself. If the pipeline changes what it writes, this fails and names the field, rather
 * than the import quietly rejecting every row in a batch.
 */
class FlashcardImportContractTest {

    private static final Path SCHEMA = Path.of("data_pipeline", "schemas", "json", "flashcard.schema.json");

    private static JsonNode schema;

    static boolean schemaIsPresent() {
        return Files.exists(SCHEMA);
    }

    @BeforeAll
    static void load() throws Exception {
        if (schemaIsPresent()) {
            schema = new ObjectMapper().readTree(Files.readString(SCHEMA));
        }
    }

    /** Follows a {@code $ref} into {@code $defs}, which is how the generator writes its nested shapes. */
    private static JsonNode definitionOf(String property) {
        JsonNode node = schema.path("properties").path(property);
        String ref = node.path("$ref").asText(null);
        if (ref == null) {
            return node;
        }
        return schema.path("$defs").path(ref.substring(ref.lastIndexOf('/') + 1));
    }

    @Test
    @EnabledIf("schemaIsPresent")
    void readsTheDefinitionAsAnEnglishVietnamesePair() {
        JsonNode definition = definitionOf("definition");

        assertThat(definition.path("type").asText()).isEqualTo("object");
        assertThat(definition.path("properties").fieldNames()).toIterable().contains("en", "vi");
        // Both required, which is why the importer can insist on a Vietnamese definition without turning away
        // well-formed generated content.
        assertThat(definition.path("required")).extracting(JsonNode::asText).contains("en", "vi");
    }

    @Test
    @EnabledIf("schemaIsPresent")
    void readsAnExampleAsASentenceWithItsTranslation() {
        JsonNode example = schema.path("$defs").path("Example");

        assertThat(example.path("properties").fieldNames()).toIterable().contains("sentence", "translation");
        assertThat(example.path("required")).extracting(JsonNode::asText).contains("sentence", "translation");
    }

    /** The flat fields the importer reads straight off a row. A rename here silently empties a column. */
    @Test
    @EnabledIf("schemaIsPresent")
    void readsTheFlatFieldsByTheNamesTheSchemaGivesThem() {
        assertThat(schema.path("properties").fieldNames()).toIterable().contains("lemma", "pos", "sense_label_en",
                "ipa_us", "ipa_uk", "audio_url_us", "audio_url_uk", "cefr_level", "mnemonic_tip_vi", "examples");
    }

    /**
     * Every field the importer refuses a row for is one the pipeline promises, so a well-formed batch imports whole. If
     * the pipeline ever stopped requiring one of these, the importer would start rejecting valid content and this is
     * where that shows.
     */
    @Test
    @EnabledIf("schemaIsPresent")
    void requiresNothingThePipelineDoesNotPromise() {
        assertThat(schema.path("required")).extracting(JsonNode::asText).contains("lemma", "pos", "sense_label_en",
                "ipa_us", "definition", "examples");
    }

    /** The importer keeps one example. The schema guarantees at least one exists, so it never keeps nothing. */
    @Test
    @EnabledIf("schemaIsPresent")
    void alwaysHasAtLeastOneExampleToKeep() {
        assertThat(schema.path("properties").path("examples").path("minItems").asInt()).isGreaterThanOrEqualTo(1);
    }

    private static final Path BATCH_SCHEMA = Path.of("data_pipeline", "schemas", "json", "flashcard_batch.schema.json");

    static boolean batchSchemaIsPresent() {
        return Files.exists(BATCH_SCHEMA);
    }

    /**
     * The file around the cards, not just the cards. The card schema above was checked from the start and the file
     * schema was not, which is how the importer came to refuse every real batch as "not a list".
     */
    @Test
    @EnabledIf("batchSchemaIsPresent")
    void readsTheCardsOutOfTheArrayTheBatchSchemaNames() throws Exception {
        JsonNode batch = new ObjectMapper().readTree(Files.readString(BATCH_SCHEMA));

        assertThat(batch.path("properties").path("flashcards").path("type").asText()).isEqualTo("array");
    }
}
