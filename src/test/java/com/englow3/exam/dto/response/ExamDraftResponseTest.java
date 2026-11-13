package com.englow3.exam.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.result.ExamDraftResult;

class ExamDraftResponseTest {

    /** A reload must bring back what was typed, or the learner loses every gap they filled in. */
    @Test
    void carriesTheTypedAnswerBack() {
        UUID question = UUID.randomUUID();

        ExamDraftResponse response = ExamDraftResponse.from(
                new ExamDraftResult(List.of(new SubmittedAnswer(question, List.of(), "museum")), 3, Instant.now()));

        assertThat(response.answers()).singleElement().satisfies(answer -> {
            assertThat(answer.questionId()).isEqualTo(question);
            assertThat(answer.textAnswer()).isEqualTo("museum");
        });
    }
}
