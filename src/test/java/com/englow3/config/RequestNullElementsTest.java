package com.englow3.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.englow3.dictation.dto.request.AddDictationSentencesRequest;
import com.englow3.exam.dto.request.SubmitExamAttemptRequest;
import com.englow3.flashcard.dto.request.AddFlashcardsRequest;
import com.englow3.quiz.dto.request.AddQuizQuestionsRequest;
import com.englow3.quiz.dto.request.SubmitQuizAttemptRequest;
import com.englow3.speaking.dto.request.CreateSpeakingPromptRequest;
import com.englow3.user.dto.request.SelectLearningPurposesRequest;
import com.englow3.user.dto.request.SelectTargetSkillsRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * A list with a null in it - {@code "answers": [null]} - passed validation, because {@code @Valid} on an element only
 * checks elements that exist, and the null then failed as a NullPointerException and a 500 on six endpoints. Every
 * collection in a request now says its elements are required, so the same body is a 400 naming the field.
 */
class RequestNullElementsTest {

    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    private static <T> List<T> withNull() {
        return Arrays.asList((T) null);
    }

    @Test
    void refusesANullAnswerInAnExamSubmission() {
        assertThat(VALIDATOR.validate(new SubmitExamAttemptRequest(withNull()))).isNotEmpty();
    }

    @Test
    void refusesANullAnswerInAQuizSubmission() {
        assertThat(VALIDATOR.validate(new SubmitQuizAttemptRequest(withNull()))).isNotEmpty();
    }

    @Test
    void refusesANullCardSentenceOrQuestion() {
        assertThat(VALIDATOR.validate(new AddFlashcardsRequest(withNull()))).isNotEmpty();
        assertThat(VALIDATOR.validate(new AddDictationSentencesRequest(withNull()))).isNotEmpty();
        assertThat(VALIDATOR.validate(new AddQuizQuestionsRequest(withNull()))).isNotEmpty();
    }

    @Test
    void refusesANullPurposeOrSkill() {
        assertThat(VALIDATOR.validate(new SelectLearningPurposesRequest(new HashSet<>(withNull())))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SelectTargetSkillsRequest(new HashSet<>(withNull())))).isNotEmpty();
    }

    /** A missing list used to be read as empty, and an empty skill list wipes the learner's choices. */
    @Test
    void refusesAMissingSkillListButAcceptsAnEmptyOne() {
        assertThat(VALIDATOR.validate(new SelectTargetSkillsRequest(null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new SelectTargetSkillsRequest(Set.of()))).isEmpty();
    }

    /** Every other kind of content already insisted on a CEFR band; a speaking prompt took any two characters. */
    @Test
    void refusesASpeakingPromptLevelThatIsNotACefrBand() {
        assertThat(VALIDATOR.validate(new CreateSpeakingPromptRequest("slug", "Title", "SOUNDS", "ZZ", "Hello there.",
                null, null, null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new CreateSpeakingPromptRequest("slug", "Title", "SOUNDS", "B1", "Hello there.",
                null, null, null, null))).isEmpty();
    }

    @Test
    void refusesACategoryOutsideTheFixedList() {
        assertThat(VALIDATOR.validate(new CreateSpeakingPromptRequest("slug", "Title", "Minimal Pairs", "B1",
                "Hello there.", null, null, null, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new com.englow3.quiz.dto.request.CreateQuizRequest("slug", "Title", null,
                "Grammar", "B1", 600, (short) 60, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new com.englow3.quiz.dto.request.CreateQuizRequest("slug", "Title", null,
                "GRAMMAR", "B1", 600, (short) 60, null))).isEmpty();
    }

    @Test
    void refusesACertificateOtherThanIeltsOrToeic() {
        assertThat(VALIDATOR.validate(new com.englow3.quiz.dto.request.CreateQuizRequest("slug", "Title", null,
                "GRAMMAR", "B1", 600, (short) 60, "TOEFL"))).isNotEmpty();
        assertThat(VALIDATOR.validate(new com.englow3.quiz.dto.request.CreateQuizRequest("slug", "Title", null,
                "GRAMMAR", "B1", 600, (short) 60, "IELTS"))).isEmpty();
        assertThat(VALIDATOR.validate(new com.englow3.dictation.dto.request.CreateDictationLessonRequest("slug",
                "Title", "Travel", "B1", "SAT"))).isNotEmpty();
    }
}
