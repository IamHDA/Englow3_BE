package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.learning.repository.FlashcardRepository;
import com.englow3.learning.repository.FlashcardSetRepository;
import com.englow3.shared.error.ConflictException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * Importing a generated batch into a real set.
 * <p>
 * The parser is covered on its own; this is the half that needs a database - whether the cards land, where they land in
 * the set's order, and what the import refuses to write into.
 */
class FlashcardImportIntegrationTest extends PostgresIntegrationTest {

    private static final String TWO_CARDS = """
            [{"lemma":"agenda","pos":"noun","ipa_us":"/əˈdʒendə/","sense_label_en":"agenda (meeting)",
              "definition":"A list of items to discuss.","definition_vi":"Chuong trinh nghi su.","examples":["Send the agenda."]},
             {"lemma":"brief","pos":"adjective","ipa_us":"/briːf/","sense_label_en":"brief (short)",
              "definition":"Lasting a short time.","definition_vi":"Ngan gon.","examples":["A brief meeting."]}]
            """;

    @Autowired
    private AdminFlashcardService service;

    @Autowired
    private FlashcardSetRepository setRepo;

    @Autowired
    private FlashcardRepository cardRepo;

    @Autowired
    private JdbcClient jdbc;

    private UUID setId;

    @BeforeEach
    void setUp() {
        UUID author = new LearnerFixture(jdbc).learner();
        setId = new LearnerFixture(jdbc).draftFlashcardSet("Imported", author);
    }

    @Test
    void writesNothingOnADryRun() {
        var report = service.validateImport(TWO_CARDS);

        assertThat(report.committed()).isFalse();
        assertThat(report.acceptedCount()).isEqualTo(2);
        assertThat(cardRepo.countByFlashcardSetId(setId)).isZero();
    }

    @Test
    void storesTheCardsAndSaysSo() {
        var report = service.importCards(setId, TWO_CARDS);

        assertThat(report.committed()).isTrue();
        assertThat(report.acceptedCount()).isEqualTo(2);
        assertThat(cardRepo.countByFlashcardSetId(setId)).isEqualTo(2);
    }

    /** Imported cards join the set at the end, numbered after whatever is already there. */
    @Test
    void numbersImportedCardsAfterTheOnesAlreadyInTheSet() {
        service.importCards(setId, TWO_CARDS);
        service.importCards(setId, """
                [{"lemma":"concur","pos":"verb","ipa_us":"/kənˈkɜːr/","sense_label_en":"concur",
                  "definition":"To agree.","definition_vi":"Dong y.","examples":["I concur."]}]
                """);

        assertThat(cardRepo.findMaxOrderNo(setId)).contains(3);
    }

    /** A file with some bad rows still imports the good ones, and the report says which were left out. */
    @Test
    void importsWhatItCanAndReportsTheRest() {
        var report = service.importCards(setId,
                """
                        [{"lemma":"agenda","pos":"noun","ipa_us":"/a/","sense_label_en":"agenda","definition":"A list.","definition_vi":"Danh sach.",
                          "examples":["Send it."]},
                         {"sense_label_en":"x","definition":"No lemma.","examples":["An example."]}]
                        """);

        assertThat(report.acceptedCount()).isEqualTo(1);
        assertThat(report.rejectedCount()).isEqualTo(1);
        assertThat(cardRepo.countByFlashcardSetId(setId)).isEqualTo(1);
    }

    /**
     * The rule worth having a test for. Generated content is not reviewed content, and an import that could write into
     * a published set would be a way around the review workflow - cards appearing under learners mid-study, approved by
     * nobody.
     */
    @Test
    void refusesToImportIntoAPublishedSet() {
        var set = setRepo.findById(setId).orElseThrow();
        set.publish(1, Instant.now());
        setRepo.save(set);

        assertThatThrownBy(() -> service.importCards(setId, TWO_CARDS)).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("FLASHCARD_SET_NOT_DRAFT");

        assertThat(cardRepo.countByFlashcardSetId(setId)).isZero();
    }

    /** An empty file is a file that does nothing, and does not need a set to be in any particular state to say so. */
    @Test
    void doesNothingForAFileWithNoUsableRows() {
        var report = service.importCards(setId, "[]");

        assertThat(report.committed()).isTrue();
        assertThat(report.acceptedCount()).isZero();
        assertThat(cardRepo.countByFlashcardSetId(setId)).isZero();
    }
}
