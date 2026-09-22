package com.englow3.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.tutor.service.TutorPrompt.Turn;

/**
 * What the tutor is actually asked. The request is the only part of this feature the project controls - the answer
 * comes from somewhere else - so it is the part worth pinning down.
 */
class TutorPromptTest {

    private static Turn learner(String content) {
        return new Turn(true, content);
    }

    private static Turn tutor(String content) {
        return new Turn(false, content);
    }

    @Nested
    class Trimming {

        /**
         * Trimmed from the front. The turn immediately before the question is the one it most likely refers to, so
         * dropping that to keep the opening of a long thread would lose what makes the question make sense.
         */
        @Test
        void keepsTheMostRecentTurnsWhenThereAreTooMany() {
            List<Turn> history = new ArrayList<>();
            for (int i = 1; i <= 30; i++) {
                history.add(learner("question " + i));
            }

            List<Turn> kept = TutorPrompt.trim(history);

            assertThat(kept).hasSize(TutorPrompt.MAX_HISTORY_TURNS);
            assertThat(kept.get(kept.size() - 1).content()).isEqualTo("question 30");
        }

        /** Order must survive the trim: a transcript read backwards is a different conversation. */
        @Test
        void keepsThemInTheOrderTheyHappened() {
            List<Turn> kept = TutorPrompt
                    .trim(List.of(learner("first"), tutor("second"), learner("third"), tutor("fourth")));

            assertThat(kept).extracting(Turn::content).containsExactly("first", "second", "third", "fourth");
        }

        /**
         * A failed turn has no content. Sending "Tutor: " with nothing after it would teach the model that answering
         * with silence is one of its options.
         */
        @Test
        void dropsATurnThatNeverGotAnAnswer() {
            List<Turn> kept = TutorPrompt
                    .trim(List.of(learner("what is a gerund?"), tutor(null), learner("are you there?"), tutor("   ")));

            assertThat(kept).extracting(Turn::content).containsExactly("what is a gerund?", "are you there?");
        }

        /**
         * One learner pasting an essay must not push the request past what the adapter accepts. Three turns is well
         * under the turn limit, so length is the only thing that can stop this - and it stops before the turn that
         * would breach the cap, not after.
         */
        @Test
        void stopsOnLengthEvenWhenTheTurnCountIsFine() {
            String essay = "x".repeat(TutorPrompt.MAX_HISTORY_CHARACTERS);

            List<Turn> kept = TutorPrompt.trim(List.of(learner("older"), learner(essay), learner("newest")));

            assertThat(kept).extracting(Turn::content).containsExactly("newest");
        }

        /**
         * A single turn over the cap is still kept. Dropping it would send a question with no context at all, which is
         * worse than a long request, and the adapter refuses what it cannot take anyway.
         */
        @Test
        void keepsOneOversizedTurnRatherThanSendingNothing() {
            String essay = "x".repeat(TutorPrompt.MAX_HISTORY_CHARACTERS * 2);

            assertThat(TutorPrompt.trim(List.of(learner(essay)))).hasSize(1);
        }

        @Test
        void handlesAConversationThatHasNotStarted() {
            assertThat(TutorPrompt.trim(List.of())).isEmpty();
        }
    }

    @Nested
    class TheRequest {

        /**
         * A first question travels alone - there is no transcript to explain, and a header over nothing reads oddly.
         */
        @Test
        void sendsAFirstQuestionOnItsOwn() {
            assertThat(TutorPrompt.userPrompt(List.of(), "  What is a gerund?  ")).isEqualTo("What is a gerund?");
        }

        @Test
        void labelsWhoSaidWhatAndEndsOnTheNewQuestion() {
            String prompt = TutorPrompt.userPrompt(
                    List.of(learner("What is a gerund?"), tutor("A verb used as a noun.")), "Give me an example.");

            assertThat(prompt).contains("Learner: What is a gerund?").contains("Tutor: A verb used as a noun.")
                    .endsWith("Give me an example.");
        }

        /** The learner's question is the last thing in the prompt, after the history, not buried in it. */
        @Test
        void putsTheQuestionAfterTheTranscript() {
            String prompt = TutorPrompt.userPrompt(List.of(learner("earlier")), "later");

            assertThat(prompt.indexOf("earlier")).isLessThan(prompt.indexOf("later"));
        }
    }

    @Nested
    class TheInstructions {

        /**
         * The load-bearing instruction. A model that guesses a grammar rule sounds exactly like one that knows it, and
         * a learner has no way to tell the difference.
         */
        @Test
        void tellsTheModelNotToInventAnAnswer() {
            assertThat(TutorPrompt.SYSTEM_PROMPT).contains("Do not invent");
        }

        @Test
        void tellsTheModelToAnswerInTheLearnersLanguage() {
            assertThat(TutorPrompt.SYSTEM_PROMPT).contains("Vietnamese");
        }

        /** Stored on every message, so an answer that reads oddly can be traced to the rules that produced it. */
        @Test
        void carriesAVersion() {
            assertThat(TutorPrompt.VERSION).isNotBlank();
        }
    }
}
