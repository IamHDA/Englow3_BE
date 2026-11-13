package com.englow3.dictation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.dictation.dto.command.SubmitDictationCommand;
import com.englow3.dictation.dto.result.DictationSentenceResult;
import com.englow3.dictation.entity.DictationAttempt;
import com.englow3.dictation.entity.DictationLesson;
import com.englow3.dictation.entity.DictationSentence;
import com.englow3.dictation.repository.DictationAttemptRepository;
import com.englow3.dictation.repository.DictationLessonRepository;
import com.englow3.dictation.repository.DictationSentenceRepository;
import com.englow3.dictation.service.impl.DictationServiceImpl;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.UserDirectory;

/**
 * What this service owes the practice screen. The transcript goes down with the lesson so an answer is marked in the
 * browser the moment it is typed (a product decision - a check that waits on a round trip for every line is the thing
 * learners complained about). What stays server-side is the record: a submission is scored again from the stored
 * transcript, so the score that is kept cannot be made up by the client.
 */
class DictationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    private static final String TRANSCRIPT = "The cat sat on the mat.";

    private final DictationLessonRepository lessonRepo = mock(DictationLessonRepository.class);
    private final DictationSentenceRepository sentenceRepo = mock(DictationSentenceRepository.class);
    private final DictationAttemptRepository attemptRepo = mock(DictationAttemptRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);

    private final DictationService service = new DictationServiceImpl(lessonRepo, sentenceRepo, attemptRepo,
            userDirectory, presignedUrls, CLOCK, "learning", Duration.ofHours(3));

    private final UUID userId = UUID.randomUUID();
    private DictationLesson lesson;
    private DictationSentence sentence;

    @BeforeEach
    void setUp() {
        lesson = DictationLesson.draft("airport", "At the airport", "travel", "A2", UUID.randomUUID());
        sentence = DictationSentence.of(lesson.getId(), 1, TRANSCRIPT, "Con mèo ngồi trên tấm thảm.",
                "dictation/airport/1.mp3", 4, 6, "T c s o t m", "cat", "The ___ sat ___ the ___");

        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(sentenceRepo.findById(sentence.getId())).thenReturn(Optional.of(sentence));
        when(sentenceRepo.findByDictationLessonIdOrderByOrderNo(lesson.getId())).thenReturn(List.of(sentence));
        when(attemptRepo.findBestAccuracyBySentence(any(), anyList())).thenReturn(List.of());
        when(attemptRepo.findLastPractisedAtByLesson(any(), anyCollection())).thenReturn(List.of());
        when(presignedUrls.resolve("learning", "dictation/airport/1.mp3", Duration.ofHours(3)))
                .thenReturn("https://storage.example/dictation/airport/1.mp3");
    }

    private void lessonIsPublished() {
        lesson.publish(1, Instant.now());
        when(lessonRepo.findById(lesson.getId())).thenReturn(Optional.of(lesson));
    }

    @Nested
    class TheAnswerKey {

        /** The screen marks answers itself, so the transcript and its translation travel with the practice payload. */
        @Test
        void sendsTheTranscriptWithThePracticePayload() {
            lessonIsPublished();

            DictationSentenceResult result = service.lessonDetail(lesson.getId()).sentences().get(0);

            assertThat(result.text()).isEqualTo(TRANSCRIPT);
            assertThat(result.translationVi()).isEqualTo("Con mèo ngồi trên tấm thảm.");
        }

        /** The hints still arrive - stripping the answer must not strip what the learner is meant to work from. */
        @Test
        void stillSendsTheHints() {
            lessonIsPublished();

            DictationSentenceResult result = service.lessonDetail(lesson.getId()).sentences().get(0);

            assertThat(result.hintWordCount()).isEqualTo(6);
            assertThat(result.hintFirstLetters()).isEqualTo("T c s o t m");
            assertThat(result.hintPartialTranscript()).isEqualTo("The ___ sat ___ the ___");
            assertThat(result.audioUrl()).isEqualTo("https://storage.example/dictation/airport/1.mp3");
        }

        /**
         * The stored score comes from the server's own marking of the stored transcript, whatever the screen showed.
         */
        @Test
        void sendsTheTranscriptBackOnceAnAnswerIsIn() {
            lessonIsPublished();

            var result = service.submit(new SubmitDictationCommand(sentence.getId(), "The cat sat on the mat"));

            assertThat(result.correctText()).isEqualTo(TRANSCRIPT);
            assertThat(result.translationVi()).isEqualTo("Con mèo ngồi trên tấm thảm.");
        }
    }

    @Nested
    class Submitting {

        @Test
        void recordsWhatTheLearnerTypedAndHowItScored() {
            lessonIsPublished();

            service.submit(new SubmitDictationCommand(sentence.getId(), "The cat sat on the mat"));

            ArgumentCaptor<DictationAttempt> saved = ArgumentCaptor.forClass(DictationAttempt.class);
            verify(attemptRepo).save(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(userId);
            assertThat(saved.getValue().getResponse()).isEqualTo("The cat sat on the mat");
            assertThat(saved.getValue().getAccuracyPercent()).isEqualByComparingTo("100.00");
        }

        /**
         * The server says whether the line is cleared, by the one rule that decides it. Before this the review screen
         * compared the accuracy to its own number - 100, where everything else says 80.
         */
        @Test
        void saysWhetherTheLineIsNowCleared() {
            lessonIsPublished();

            assertThat(service.submit(new SubmitDictationCommand(sentence.getId(), "The cat sat on the mat")).cleared())
                    .isTrue();
            // Five words of six is 83%: cleared, though not perfect. The review screen used to call this wrong.
            assertThat(service.submit(new SubmitDictationCommand(sentence.getId(), "The cat sat on the")).cleared())
                    .isTrue();
            assertThat(service.submit(new SubmitDictationCommand(sentence.getId(), "The dog")).cleared()).isFalse();
        }

        /** A learner who submits having typed nothing scores zero rather than crashing the request. */
        @Test
        void scoresAnEmptyAnswerAsNothingRatherThanFailing() {
            lessonIsPublished();

            var result = service.submit(new SubmitDictationCommand(sentence.getId(), null));

            assertThat(result.accuracyPercent()).isEqualByComparingTo("0.00");
            assertThat(result.response()).isEmpty();
        }

        /**
         * A sentence id is guessable, so the lesson's status is re-checked at submission. Without it, an unpublished
         * draft answers with its own transcript to anyone who submits a blank line against it.
         */
        @Test
        void refusesToMarkALineFromAnUnpublishedLesson() {
            when(lessonRepo.findById(lesson.getId())).thenReturn(Optional.of(lesson));

            assertThatThrownBy(() -> service.submit(new SubmitDictationCommand(sentence.getId(), "anything")))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("DICTATION_LESSON_NOT_FOUND");

            verify(attemptRepo, never()).save(any());
        }

        /**
         * A missing lesson and an unpublished one answer alike, so an id cannot be used to find out which drafts exist.
         */
        @Test
        void answersAMissingLessonTheSameWay() {
            when(lessonRepo.findById(lesson.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(new SubmitDictationCommand(sentence.getId(), "anything")))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("DICTATION_LESSON_NOT_FOUND");
        }

        @Test
        void refusesASentenceThatDoesNotExist() {
            UUID unknown = UUID.randomUUID();
            when(sentenceRepo.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(new SubmitDictationCommand(unknown, "anything")))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("DICTATION_SENTENCE_NOT_FOUND");
        }
    }

    @Nested
    class Progress {

        /** Nobody has attempted it, so it is not cleared - and the screen shows no accuracy rather than a zero. */
        @Test
        void countsNothingAsDoneBeforeTheFirstAttempt() {
            lessonIsPublished();

            var detail = service.lessonDetail(lesson.getId());

            assertThat(detail.lesson().completedSentenceCount()).isZero();
            assertThat(detail.sentences().get(0).bestAccuracyPercent()).isNull();
        }

        @Test
        void countsASentenceOnceItsBestAttemptClearsTheThreshold() {
            lessonIsPublished();
            when(attemptRepo.findBestAccuracyBySentence(any(), anyList()))
                    .thenReturn(List.<Object[]> of(new Object[] { sentence.getId(), new BigDecimal("83.33") }));

            var detail = service.lessonDetail(lesson.getId());

            assertThat(detail.lesson().completedSentenceCount()).isEqualTo(1);
            assertThat(detail.sentences().get(0).bestAccuracyPercent()).isEqualByComparingTo("83.33");
        }

        /** Shown as practised, not as done: the accuracy still reaches the screen, the completion count does not. */
        @Test
        void doesNotCountASentenceStillShortOfTheThreshold() {
            lessonIsPublished();
            when(attemptRepo.findBestAccuracyBySentence(any(), anyList()))
                    .thenReturn(List.<Object[]> of(new Object[] { sentence.getId(), new BigDecimal("66.67") }));

            var detail = service.lessonDetail(lesson.getId());

            assertThat(detail.lesson().completedSentenceCount()).isZero();
            assertThat(detail.sentences().get(0).bestAccuracyPercent()).isEqualByComparingTo("66.67");
        }
    }
}
