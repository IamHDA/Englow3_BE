package com.englow3.tutor.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

/** One turn of a conversation, and what may happen to it. */
class TutorMessageTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private static TutorMessage pendingReply() {
        return TutorMessage.awaitingReply(CONVERSATION_ID, 2);
    }

    private static TutorMessage answeredReply() {
        TutorMessage message = pendingReply();
        message.answer("A gerund is a verb used as a noun.", "gpt-4o-mini", "tutor-chat-v1", 120, 40, Instant.now());
        return message;
    }

    @Nested
    class TheLearnersTurn {

        @Test
        void isReadyTheMomentItArrives() {
            TutorMessage message = TutorMessage.fromLearner(CONVERSATION_ID, 1, "  What is a gerund?  ");

            assertThat(message.getRole()).isEqualTo(TutorMessageRole.USER);
            assertThat(message.getStatus()).isEqualTo(TutorMessageStatus.READY);
            assertThat(message.getContent()).isEqualTo("What is a gerund?");
        }

        @Test
        void cannotBeEmpty() {
            assertThatThrownBy(() -> TutorMessage.fromLearner(CONVERSATION_ID, 1, "   "))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("TUTOR_MESSAGE_EMPTY");
        }
    }

    @Nested
    class TheTutorsTurn {

        /**
         * The row exists before the answer does, so the screen has something to show as pending and the reply has
         * somewhere to land that is already in the right place in the transcript.
         */
        @Test
        void startsEmptyAndPending() {
            TutorMessage message = pendingReply();

            assertThat(message.getRole()).isEqualTo(TutorMessageRole.ASSISTANT);
            assertThat(message.getStatus()).isEqualTo(TutorMessageStatus.PENDING);
            assertThat(message.getContent()).isNull();
        }

        @Test
        void recordsWhatAnsweredItAndWhatItCost() {
            TutorMessage message = answeredReply();

            assertThat(message.getStatus()).isEqualTo(TutorMessageStatus.READY);
            assertThat(message.getModel()).isEqualTo("gpt-4o-mini");
            assertThat(message.getPromptVersion()).isEqualTo("tutor-chat-v1");
            assertThat(message.getInputTokens()).isEqualTo(120);
            assertThat(message.getAnsweredAt()).isNotNull();
        }

        /** An answer of only whitespace is not an answer, and an empty bubble is worse than an explanation. */
        @Test
        void refusesAnAnswerWithNothingInIt() {
            assertThatThrownBy(() -> pendingReply().answer("   ", "gpt-4o-mini", "v1", 1, 1, Instant.now()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("TUTOR_REPLY_EMPTY");
        }

        /**
         * A job can run twice - a stall reclaim is exactly that - and the second run must not overwrite an answer the
         * learner is already reading.
         */
        @Test
        void refusesToBeAnsweredTwice() {
            TutorMessage message = answeredReply();

            assertThatThrownBy(() -> message.answer("something else", "m", "v1", 1, 1, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("TUTOR_MESSAGE_NOT_PENDING");
        }

        @Test
        void recordsWhyNoAnswerCame() {
            TutorMessage message = pendingReply();

            message.fail("TUTOR_SERVICE_UNREACHABLE", Instant.now());

            assertThat(message.getStatus()).isEqualTo(TutorMessageStatus.FAILED);
            assertThat(message.getErrorCode()).isEqualTo("TUTOR_SERVICE_UNREACHABLE");
        }

        /** A failure arriving after an answer would replace something the learner can already see. */
        @Test
        void refusesToFailATurnThatAlreadyAnswered() {
            assertThatThrownBy(() -> answeredReply().fail("TOO_LATE", Instant.now()))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class Reporting {

        @Test
        void keepsTheLearnersReasonInTheirOwnWords() {
            TutorMessage message = answeredReply();

            message.report("  That rule is wrong.  ", Instant.now());

            assertThat(message.getReportedAt()).isNotNull();
            assertThat(message.getReportNote()).isEqualTo("That rule is wrong.");
        }

        /** A learner who can see the answer is wrong should not have to explain why in order to say so. */
        @Test
        void acceptsAReportWithNoNote() {
            TutorMessage message = answeredReply();

            message.report(null, Instant.now());

            assertThat(message.getReportedAt()).isNotNull();
            assertThat(message.getReportNote()).isNull();
        }

        /** The answer stays visible. Hiding it on report would lose the exact thing being reported. */
        @Test
        void leavesTheAnswerWhereItIs() {
            TutorMessage message = answeredReply();

            message.report("wrong", Instant.now());

            assertThat(message.getStatus()).isEqualTo(TutorMessageStatus.READY);
            assertThat(message.getContent()).isEqualTo("A gerund is a verb used as a noun.");
        }

        /** Reporting your own message means nothing, and would put rows in a queue no reviewer can act on. */
        @Test
        void refusesToReportTheLearnersOwnMessage() {
            TutorMessage own = TutorMessage.fromLearner(CONVERSATION_ID, 1, "What is a gerund?");

            assertThatThrownBy(() -> own.report("oops", Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("TUTOR_MESSAGE_NOT_REPORTABLE");
        }

        @Test
        void refusesToReportATurnThatNeverAnswered() {
            assertThatThrownBy(() -> pendingReply().report("wrong", Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("TUTOR_MESSAGE_NOT_ANSWERED");
        }
    }
}
