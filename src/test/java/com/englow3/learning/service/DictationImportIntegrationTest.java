package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.learning.entity.DictationSentence;
import com.englow3.shared.error.BadRequestException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * Importing a shadowing batch into real lessons.
 * <p>
 * The parser is covered on its own. This is the half that needs a database: whether the offsets survive the round trip,
 * whether the constraint holds, and whether running the same batch twice is safe.
 */
class DictationImportIntegrationTest extends PostgresIntegrationTest {

    private static String batch(String clipId) {
        return """
                {"clips":[{"clip_id":"%s","cefr_level":"A2","accent":"us","script":"At the airport.",
                           "audio_url":"shadowing/%s.mp3","duration_ms":8400,
                           "segments":[{"order":1,"text":"Where is the gate?","start_ms":0,"end_ms":2100},
                                       {"order":2,"text":"It is over there.","start_ms":2100,"end_ms":4300}]}]}
                """.formatted(clipId, clipId);
    }

    @Autowired
    private AdminDictationService service;

    @Autowired
    private JdbcClient jdbc;

    private String clipId;

    @BeforeEach
    void setUp() {
        // Signed in the way a request is, so the author is resolved through the real directory. This used to swap a
        // mocked directory into the service bean - a singleton the whole suite shares - and never put it back.
        SignedIn.as(jdbc, new LearnerFixture(jdbc).learner());

        clipId = "clip-" + UUID.randomUUID();
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    private long sentenceCount(String slug) {
        return jdbc.sql("""
                select count(*) from dictation_sentences s
                  join dictation_lessons l on l.id = s.dictation_lesson_id
                 where l.slug = :slug
                """).param("slug", slug).query(Long.class).single();
    }

    @Test
    void writesNothingOnADryRun() {
        var report = service.validateImport(batch(clipId));

        assertThat(report.committed()).isFalse();
        assertThat(report.acceptedCount()).isEqualTo(1);
        assertThat(sentenceCount(clipId)).isZero();
    }

    @Test
    void storesALessonPerClipAndASentencePerSegment() {
        var report = service.importLessons(batch(clipId));

        assertThat(report.committed()).isTrue();
        assertThat(report.acceptedCount()).isEqualTo(1);
        assertThat(report.sentenceCount()).isEqualTo(2);
        assertThat(sentenceCount(clipId)).isEqualTo(2);
    }

    /** The point of the whole exercise: the windows survive the round trip, so the player can seek to them. */
    @Test
    void keepsEachSentencesWindowIntoTheRecording() {
        service.importLessons(batch(clipId));

        var windows = jdbc.sql("""
                select s.audio_start_ms, s.audio_end_ms, s.audio_object_key
                  from dictation_sentences s join dictation_lessons l on l.id = s.dictation_lesson_id
                 where l.slug = :slug order by s.order_no
                """).param("slug", clipId).query((rs, row) -> new int[] { rs.getInt(1), rs.getInt(2) }).list();

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0)).containsExactly(0, 2100);
        assertThat(windows.get(1)).containsExactly(2100, 4300);
    }

    /** Every sentence plays from one file - that is what made the offsets necessary. */
    @Test
    void pointsEverySentenceAtTheClipsOwnRecording() {
        service.importLessons(batch(clipId));

        var keys = jdbc.sql("""
                select distinct s.audio_object_key from dictation_sentences s
                  join dictation_lessons l on l.id = s.dictation_lesson_id where l.slug = :slug
                """).param("slug", clipId).query(String.class).list();

        assertThat(keys).containsExactly("shadowing/%s.mp3".formatted(clipId));
    }

    /**
     * Re-running a batch is safe. An import that failed half-way, or a file sent twice, should not leave an author
     * deleting thirty lessons to retry one.
     */
    @Test
    void skipsAClipThatIsAlreadyImportedRatherThanFailing() {
        service.importLessons(batch(clipId));

        var second = service.importLessons(batch(clipId));

        assertThat(second.acceptedCount()).isZero();
        assertThat(second.rejections()).singleElement()
                .satisfies(rejection -> assertThat(rejection.reason()).isEqualTo("Already imported"));
        assertThat(sentenceCount(clipId)).isEqualTo(2);
    }

    /** Lessons arrive as drafts. Generated content is not reviewed content. */
    @Test
    void importsAsADraft() {
        service.importLessons(batch(clipId));

        var status = jdbc.sql("select status from dictation_lessons where slug = :slug").param("slug", clipId)
                .query(String.class).single();

        assertThat(status).isEqualTo("DRAFT");
    }

    /**
     * The database holds the rule too. A window with no end would let the player run to the end of a ten-minute
     * recording for a four-second sentence, and the constraint is what stops that arriving by any other route.
     */
    @Test
    void refusesHalfAWindowAtBothLayers() {
        assertThatThrownBy(() -> DictationSentence.of(UUID.randomUUID(), 1, "A line.", null, "a.mp3", 4, 2, null, null,
                null, 100, null)).isInstanceOf(BadRequestException.class)
                        .extracting(e -> ((BadRequestException) e).getCode())
                        .isEqualTo("DICTATION_AUDIO_WINDOW_INVALID");

        UUID lessonId = jdbc.sql("select id from dictation_lessons limit 1").query(UUID.class).optional()
                .orElseGet(() -> {
                    service.importLessons(batch(clipId));
                    return jdbc.sql("select id from dictation_lessons where slug = :slug").param("slug", clipId)
                            .query(UUID.class).single();
                });

        assertThatThrownBy(() -> jdbc.sql("""
                insert into dictation_sentences (id, dictation_lesson_id, order_no, text, audio_object_key,
                                                 audio_duration_seconds, hint_word_count, audio_start_ms, audio_end_ms)
                values (:id, :lessonId, 99, 'A line.', 'a.mp3', 4, 2, 500, 100)
                """).param("id", UUID.randomUUID()).param("lessonId", lessonId).update())
                .hasMessageContaining("chk_dictation_sentences_audio_window");
    }
}
