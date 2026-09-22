package com.englow3.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.learning.dto.command.AddDictationSentencesCommand;
import com.englow3.learning.dto.command.AddDictationSentencesCommand.NewSentence;
import com.englow3.learning.dto.command.CreateDictationLessonCommand;
import com.englow3.learning.entity.DictationLesson;
import com.englow3.learning.entity.DictationLessonStatus;
import com.englow3.learning.entity.DictationSentence;
import com.englow3.learning.repository.DictationLessonRepository;
import com.englow3.learning.repository.DictationSentenceRepository;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

/**
 * Authoring dictation lessons. The flashcard and quiz equivalents were already covered; this is the third of the three,
 * and the only one whose sentences carry a hint that has to agree with how the answer is later marked.
 */
class AdminDictationServiceTest {

    private final DictationLessonRepository lessonRepo = mock(DictationLessonRepository.class);
    private final DictationSentenceRepository sentenceRepo = mock(DictationSentenceRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);

    private final AdminDictationService service = new AdminDictationService(lessonRepo, sentenceRepo, userDirectory);

    private final UUID adminId = UUID.randomUUID();
    private DictationLesson lesson;

    @BeforeEach
    void setUp() {
        lesson = DictationLesson.draft("airport", "At the airport", "travel", "A2", adminId);

        when(userDirectory.requireCurrentUserId()).thenReturn(adminId);
        when(lessonRepo.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(lessonRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sentenceRepo.findByDictationLessonIdOrderByOrderNo(any())).thenReturn(List.of());
        when(sentenceRepo.countByDictationLessonId(any())).thenReturn(0L);
    }

    private List<DictationSentence> addOneSentence(String text) {
        service.addSentences(new AddDictationSentencesCommand(lesson.getId(),
                List.of(new NewSentence(text, "Bản dịch.", "dictation/airport/1.mp3", 4, null, null, null))));

        ArgumentCaptor<List<DictationSentence>> saved = ArgumentCaptor.captor();
        verify(sentenceRepo).saveAll(saved.capture());

        return saved.getValue();
    }

    @Nested
    class Creating {

        @Test
        void startsANewLessonAsADraft() {
            var result = service.create(new CreateDictationLessonCommand("airport", "At the airport", "travel", "A2"));

            assertThat(result.title()).isEqualTo("At the airport");
            verify(lessonRepo).save(any());
        }

        /** A slug is a URL, and a second lesson answering to the same one would make which lesson a coin toss. */
        @Test
        void refusesASlugAlreadyInUse() {
            when(lessonRepo.existsBySlug("airport")).thenReturn(true);

            assertThatThrownBy(
                    () -> service.create(new CreateDictationLessonCommand("airport", "At the airport", "travel", "A2")))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("DICTATION_LESSON_SLUG_TAKEN");

            verify(lessonRepo, never()).save(any());
        }

        /**
         * An author is not practising their own lesson, so a per-learner figure here would be a number about nobody.
         */
        @Test
        void reportsNoProgressOnALessonNobodyHasPractised() {
            var result = service.create(new CreateDictationLessonCommand("airport", "At the airport", "travel", "A2"));

            assertThat(result.completedSentenceCount()).isZero();
            assertThat(result.lastPractisedAt()).isNull();
        }
    }

    @Nested
    class AddingSentences {

        /**
         * The hint says how many words to expect, and the marking counts words its own way. Taking the count from the
         * scorer rather than from a split here is what stops a hint that says six over a sentence marked out of seven.
         */
        @Test
        void takesTheWordCountFromTheSameRuleThatMarksTheAnswer() {
            List<DictationSentence> saved = addOneSentence("The cat sat on the mat.");

            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getHintWordCount())
                    .isEqualTo(DictationScorer.words("The cat sat on the mat.").size()).isEqualTo(6);
        }

        /** Punctuation is not a word to the scorer, so it must not be one to the hint either. */
        @Test
        void doesNotCountPunctuationAsAWord() {
            List<DictationSentence> saved = addOneSentence("Well, - yes!");

            assertThat(saved.get(0).getHintWordCount()).isEqualTo(2);
        }

        /** Appending continues the existing numbering rather than restarting it. */
        @Test
        void numbersNewSentencesAfterTheOnesAlreadyThere() {
            when(sentenceRepo.countByDictationLessonId(lesson.getId())).thenReturn(3L);

            service.addSentences(new AddDictationSentencesCommand(lesson.getId(),
                    List.of(new NewSentence("One.", null, "a.mp3", 2, null, null, null),
                            new NewSentence("Two.", null, "b.mp3", 2, null, null, null))));

            ArgumentCaptor<List<DictationSentence>> saved = ArgumentCaptor.captor();
            verify(sentenceRepo).saveAll(saved.capture());
            assertThat(saved.getValue()).extracting(DictationSentence::getOrderNo).containsExactly(4, 5);
        }

        @Test
        void startsAtOneOnAnEmptyLesson() {
            List<DictationSentence> saved = addOneSentence("The cat sat on the mat.");

            assertThat(saved.get(0).getOrderNo()).isEqualTo(1);
        }

        @Test
        void refusesALessonThatDoesNotExist() {
            UUID unknown = UUID.randomUUID();
            when(lessonRepo.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addSentences(new AddDictationSentencesCommand(unknown, List.of())))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("DICTATION_LESSON_NOT_FOUND");
        }
    }

    @Nested
    class Reviewing {

        private void lessonHasSentences(long count) {
            when(sentenceRepo.countByDictationLessonId(lesson.getId())).thenReturn(count);
        }

        /**
         * The count is read now rather than trusted from submission time. Sentences live in another table, so a lesson
         * emptied after it was submitted would otherwise be published with nothing in it.
         */
        @Test
        void refusesToPublishALessonThatHasSinceLostItsSentences() {
            lessonHasSentences(0);

            assertThatThrownBy(() -> service.publish(lesson.getId())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("DICTATION_LESSON_EMPTY");
        }

        @Test
        void publishesALessonWithSentencesInIt() {
            lessonHasSentences(8);

            var result = service.publish(lesson.getId());

            assertThat(result.status()).isEqualTo(DictationLessonStatus.PUBLISHED.name());
            assertThat(result.itemCount()).isEqualTo(8);
        }

        /** Credited to whoever the token says is reviewing, never to an id the request supplied. */
        @Test
        void recordsTheReviewerFromTheToken() {
            lessonHasSentences(8);
            lesson.submitForReview(8, Instant.now());

            var result = service.approve(lesson.getId());

            assertThat(result.reviewedByUserId()).isEqualTo(adminId);
            assertThat(result.status()).isEqualTo(DictationLessonStatus.PUBLISHED.name());
        }

        @Test
        void keepsTheReasonOnARejection() {
            lessonHasSentences(8);
            lesson.submitForReview(8, Instant.now());

            var result = service.reject(lesson.getId(), "Sentence 3 has no audio.");

            assertThat(result.status()).isEqualTo(DictationLessonStatus.REJECTED.name());
            assertThat(result.reviewNote()).isEqualTo("Sentence 3 has no audio.");
        }

        /**
         * Authoring actions report the review state without loading every sentence to build a figure nobody asked for -
         * the count is one grouped query, not one row per line.
         */
        @Test
        void doesNotReadEverySentenceToReportAReviewDecision() {
            lessonHasSentences(8);

            service.publish(lesson.getId());

            verify(sentenceRepo, never()).findByDictationLessonIdOrderByOrderNo(any());
        }
    }

    @Nested
    class Authoring {

        /** A reviewer who cannot see a draft has no way to review one, so this list is not filtered to published. */
        @Test
        void listsDraftsAlongsideEverythingElse() {
            when(lessonRepo.searchForAuthoring(any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(lesson)));
            when(sentenceRepo.countByLessonIds(anyList())).thenReturn(java.util.Map.of(lesson.getId(), 8L));

            var page = service.searchForAuthoring(null, null, org.springframework.data.domain.Pageable.unpaged());

            assertThat(page.getContent()).singleElement()
                    .satisfies(result -> assertThat(result.status()).isEqualTo(DictationLessonStatus.DRAFT.name()));
        }

        /** A lesson with no sentences counts as none rather than falling out of the list. */
        @Test
        void showsALessonWithNoSentencesAsEmpty() {
            when(lessonRepo.searchForAuthoring(any(), any(), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(lesson)));
            when(sentenceRepo.countByLessonIds(anyList())).thenReturn(java.util.Map.of());

            var page = service.searchForAuthoring(null, null, org.springframework.data.domain.Pageable.unpaged());

            assertThat(page.getContent().get(0).itemCount()).isZero();
        }
    }
}
