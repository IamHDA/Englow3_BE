package com.englow3.flashcard.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reading a generated batch.
 * <p>
 * The fixtures below are the shape {@code data_pipeline/schemas/json/flashcard.schema.json} actually defines, not a
 * guess at it: a definition is an English-Vietnamese pair, and an example is a sentence with its translation. Written
 * from a guess once already, and the test then agreed with the parser because both were wrong in the same way.
 * <p>
 * The pipeline validates its own output against that schema before writing it, so this is the second check rather than
 * the first. It exists because a file that reached someone's disk is a file that could have been edited, and because
 * the two sides do not require the same things - the pipeline demands fields this module has no column for.
 */
class FlashcardImportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ONE_GOOD_CARD = """
            [{"lemma":"agenda","pos":"noun","sense_label_en":"agenda (meeting)","ipa_us":"/əˈdʒendə/",
              "ipa_uk":"/əˈdʒendə/","cefr_level":"B1","mnemonic_tip_vi":"Nho la lich hop.",
              "definition":{"en":"A list of items to discuss.","vi":"Chuong trinh nghi su."},
              "examples":[{"sentence":"Send the agenda.","translation":"Gui chuong trinh di.","source":"generated"},
                          {"sentence":"A second example.","translation":"Vi du thu hai."}]}]
            """;

    /** Everything a storable card needs, so each test below varies only the one field it is about. */
    private static String card(String lemma, String senseLabel) {
        return """
                {"lemma":"%s","pos":"noun","ipa_us":"/x/","sense_label_en":"%s",
                 "definition":{"en":"A definition.","vi":"Mot dinh nghia."},
                 "examples":[{"sentence":"An example.","translation":"Mot vi du."}]}
                """.formatted(lemma, senseLabel);
    }

    @Nested
    class ReadingACard {

        @Test
        void takesEveryFieldThisModuleHasAColumnFor() {
            var report = FlashcardImport.read(MAPPER, ONE_GOOD_CARD);

            assertThat(report.acceptedCount()).isEqualTo(1);
            assertThat(report.cards()).singleElement().satisfies(card -> {
                assertThat(card.lemma()).isEqualTo("agenda");
                assertThat(card.partOfSpeech()).isEqualTo("noun");
                assertThat(card.senseLabel()).isEqualTo("agenda (meeting)");
                assertThat(card.definitionEn()).isEqualTo("A list of items to discuss.");
                assertThat(card.cefrLevel()).isEqualTo("B1");
            });
        }

        /** A card shows one sentence. Keeping the rest in a column nothing reads would be storage pretending. */
        @Test
        void keepsTheFirstExampleAndDropsTheRest() {
            var report = FlashcardImport.read(MAPPER, ONE_GOOD_CARD);

            assertThat(report.cards().get(0).exampleSentence()).isEqualTo("Send the agenda.");
        }

        /** Both sides of the pair. A card with only the English half is half a card to a Vietnamese learner. */
        @Test
        void keepsBothSidesOfTheDefinition() {
            var report = FlashcardImport.read(MAPPER, ONE_GOOD_CARD);

            assertThat(report.cards().get(0).definitionEn()).isEqualTo("A list of items to discuss.");
            assertThat(report.cards().get(0).definitionVi()).isEqualTo("Chuong trinh nghi su.");
        }

        /** And both sides of the example, for the same reason. */
        @Test
        void keepsTheExampleWithItsTranslation() {
            var report = FlashcardImport.read(MAPPER, ONE_GOOD_CARD);

            assertThat(report.cards().get(0).exampleSentence()).isEqualTo("Send the agenda.");
            assertThat(report.cards().get(0).exampleTranslationVi()).isEqualTo("Gui chuong trinh di.");
        }

        /**
         * A definition written as a bare string is a file from somewhere else. Rejected rather than read half-way:
         * taking the string as the English side would store a card with no Vietnamese at all.
         */
        @Test
        void refusesADefinitionThatIsNotThePair() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"agenda","pos":"noun","ipa_us":"/x/","sense_label_en":"agenda",
                      "definition":"A list of items to discuss.",
                      "examples":[{"sentence":"Send it.","translation":"Gui di."}]}]
                    """);

            assertThat(report.rejections()).singleElement()
                    .satisfies(rejection -> assertThat(rejection.reason()).isEqualTo("No English definition"));
        }

        @Test
        void trimsSurroundingSpace() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"  brief  ","pos":"noun","ipa_us":"/x/","sense_label_en":"brief",
                      "definition":{"en":" A short account. ","vi":" Ngan. "},
                      "examples":[{"sentence":"  A brief note.  ","translation":" Mot ghi chu. "}]}]
                    """);

            assertThat(report.cards()).singleElement().satisfies(card -> {
                assertThat(card.lemma()).isEqualTo("brief");
                assertThat(card.exampleSentence()).isEqualTo("A brief note.");
            });
        }
    }

    /**
     * The shape the pipeline actually writes. The importer first took only a bare array and would have refused every
     * batch the pipeline has produced as "not a list".
     */
    @Nested
    class ReadingABatchFile {

        @Test
        void readsTheCardsOutOfABatch() {
            var report = FlashcardImport.read(MAPPER, """
                    {"batch_metadata":{"batch_id":"b1","module_type":"flashcard","total_records":2},
                     "flashcards":[%s,%s]}
                    """.formatted(card("agenda", "agenda"), card("brief", "brief")));

            assertThat(report.acceptedCount()).isEqualTo(2);
        }

        /**
         * One word, three parts of speech, three cards. Found in the real batches: keyed on the word and its label
         * alone, the importer kept "that" as a conjunction and dropped it as a determiner and as a pronoun.
         */
        @Test
        void keepsOneCardPerPartOfSpeech() {
            String that = """
                    {"lemma":"that","pos":"%s","sense_index":1,"ipa_us":"/ðæt/","sense_label_en":"that",
                     "definition":{"en":"Used to identify something.","vi":"do, kia"},
                     "examples":[{"sentence":"That is mine.","translation":"Cai do la cua toi."}]}
                    """;

            var report = FlashcardImport.read(MAPPER, "{\"flashcards\":[" + that.formatted("conjunction") + ","
                    + that.formatted("determiner") + "," + that.formatted("pronoun") + "]}");

            assertThat(report.acceptedCount()).isEqualTo(3);
            assertThat(report.rejections()).isEmpty();
        }

        /** Audio arrives as a URL into wherever the pipeline ran; it is stored as a key this application can sign. */
        @Test
        void storesAudioAsAKeyRatherThanTheUrlItArrivedAs() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"agenda","pos":"noun","ipa_us":"/x/","sense_label_en":"agenda",
                      "audio_url_us":"http://localhost:9000/audio/flashcards/vocab_1_us.mp3",
                      "definition":{"en":"A list.","vi":"Danh sach."},
                      "examples":[{"sentence":"Send it.","translation":"Gui di."}]}]
                    """);

            assertThat(report.cards().get(0).audioUsObjectKey()).isEqualTo("audio/flashcards/vocab_1_us.mp3");
        }
    }

    @Nested
    class Rejecting {

        /** One reason per row, and the position, because a person is going to open the file and fix it. */
        @Test
        void saysWhichRowAndWhy() {
            var report = FlashcardImport.read(MAPPER, "[" + card("agenda", "agenda") + ", {\"sense_label_en\":\"x\"}]");

            assertThat(report.acceptedCount()).isEqualTo(1);
            assertThat(report.rejections()).singleElement().satisfies(rejection -> {
                assertThat(rejection.index()).isEqualTo(2);
                assertThat(rejection.reason()).isEqualTo("No lemma");
            });
        }

        /**
         * A sense label is what separates two entries for the same word. Without it, two senses of "bank" are one card
         * as far as the set is concerned and the second silently replaces the first.
         */
        @Test
        void refusesACardWithNoSenseLabel() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"bank","pos":"noun","ipa_us":"/x/",
                      "definition":{"en":"A financial institution.","vi":"Ngan hang."},
                      "examples":[{"sentence":"At the bank.","translation":"O ngan hang."}]}]
                    """);

            assertThat(report.rejections()).singleElement()
                    .satisfies(rejection -> assertThat(rejection.reason()).isEqualTo("No sense label"));
        }

        /** A card with nothing to show the word in use teaches the word in isolation, which is not what it is for. */
        @Test
        void refusesACardWithNoExample() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"agenda","pos":"noun","ipa_us":"/x/","sense_label_en":"agenda",
                      "definition":{"en":"A list.","vi":"Danh sach."},"examples":[]}]
                    """);

            assertThat(report.rejections()).singleElement()
                    .satisfies(rejection -> assertThat(rejection.reason()).isEqualTo("No example sentence"));
        }

        /** Two senses of one word are two cards; the same sense twice is a duplicate. */
        @Test
        void keepsTwoSensesOfOneWordButRefusesTheSameSenseTwice() {
            var report = FlashcardImport.read(MAPPER, "[" + card("bank", "bank (money)") + ","
                    + card("bank", "bank (river)") + "," + card("Bank", "bank (money)") + "]");

            assertThat(report.acceptedCount()).isEqualTo(2);
            assertThat(report.rejections()).singleElement().satisfies(rejection -> {
                assertThat(rejection.index()).isEqualTo(3);
                assertThat(rejection.reason()).isEqualTo("Already in this file");
            });
        }

        /** A bad row does not stop the file - a batch of three thousand with four bad rows is worth importing. */
        @Test
        void keepsReadingAfterARowItCannotUse() {
            var report = FlashcardImport.read(MAPPER,
                    "[" + card("one", "one") + ", \"not a card\", " + card("two", "two") + "]");

            assertThat(report.acceptedCount()).isEqualTo(2);
            assertThat(report.rejectedCount()).isEqualTo(1);
        }
    }

    @Nested
    class RefusingTheWholeFile {

        /**
         * The wrong file is not three thousand bad rows. Reporting it that way would bury the one thing the person
         * needs to know under a list they cannot act on.
         */
        @Test
        void refusesSomethingThatIsNotAListOfCards() {
            assertThatThrownBy(() -> FlashcardImport.read(MAPPER, "{\"lemma\":\"agenda\"}"))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("FLASHCARD_IMPORT_NOT_A_LIST");
        }

        @Test
        void refusesSomethingThatIsNotJson() {
            assertThatThrownBy(() -> FlashcardImport.read(MAPPER, "lemma,definition\nagenda,A list."))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("FLASHCARD_IMPORT_UNREADABLE");
        }

        @Test
        void refusesAFileTooLargeToReadInOneGo() {
            String row = card("w%d", "s");
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i <= FlashcardImport.MAX_CARDS; i++) {
                json.append(row.formatted(i)).append(i < FlashcardImport.MAX_CARDS ? "," : "");
            }

            assertThatThrownBy(() -> FlashcardImport.read(MAPPER, json.append("]").toString()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("FLASHCARD_IMPORT_TOO_LARGE");
        }

        /** An empty list is a file that does nothing, not a file that is wrong. */
        @Test
        void acceptsAnEmptyFileAsDoingNothing() {
            var report = FlashcardImport.read(MAPPER, "[]");

            assertThat(report.isEmpty()).isTrue();
            assertThat(report.rejectedCount()).isZero();
        }
    }
}
