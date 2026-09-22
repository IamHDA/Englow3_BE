package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reading a generated batch.
 * <p>
 * The pipeline checks its own output against a schema before writing it, so this is the second check rather than the
 * first. It exists because a file that reached someone's disk is a file that could have been edited, and because the
 * two sides disagree about what a card needs: the pipeline requires fields with no column here, and a card is no use to
 * a learner without an example the pipeline treats as optional.
 */
class FlashcardImportTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ONE_GOOD_CARD = """
            [{"lemma":"agenda","pos":"noun","sense_label_en":"agenda (meeting)","ipa_us":"/əˈdʒendə/",
              "definition":"A list of items to discuss.","definition_vi":"Chuong trinh nghi su.","cefr_level":"B1",
              "examples":[{"text":"Send the agenda."},{"text":"A second example."}]}]
            """;

    /** Everything a storable card needs, so each test below varies only the one field it is about. */
    private static String card(String lemma, String senseLabel) {
        return """
                {"lemma":"%s","pos":"noun","ipa_us":"/x/","sense_label_en":"%s",
                 "definition":"A definition.","definition_vi":"Mot dinh nghia.","examples":["An example."]}
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

        @Test
        void keepsTheVietnameseDefinition() {
            var report = FlashcardImport.read(MAPPER, ONE_GOOD_CARD);

            assertThat(report.cards().get(0).definitionVi()).isEqualTo("Chuong trinh nghi su.");
        }

        @Test
        void trimsSurroundingSpace() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"  brief  ","pos":"noun","ipa_us":"/x/","sense_label_en":"brief",
                      "definition":" A short account. ","definition_vi":" Ngan. ","examples":["  A brief note.  "]}]
                    """);

            assertThat(report.cards()).singleElement().satisfies(card -> {
                assertThat(card.lemma()).isEqualTo("brief");
                assertThat(card.exampleSentence()).isEqualTo("A brief note.");
            });
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
                    [{"lemma":"bank","pos":"noun","ipa_us":"/x/","definition":"A financial institution.",
                      "definition_vi":"Ngan hang.","examples":["At the bank."]}]
                    """);

            assertThat(report.rejections()).singleElement()
                    .satisfies(rejection -> assertThat(rejection.reason()).isEqualTo("No sense label"));
        }

        /** A card with nothing to show the word in use teaches the word in isolation, which is not what it is for. */
        @Test
        void refusesACardWithNoExample() {
            var report = FlashcardImport.read(MAPPER, """
                    [{"lemma":"agenda","pos":"noun","ipa_us":"/x/","sense_label_en":"agenda","definition":"A list.",
                      "definition_vi":"Danh sach.","examples":[]}]
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
