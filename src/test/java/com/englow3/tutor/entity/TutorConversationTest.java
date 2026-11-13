package com.englow3.tutor.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

/** A thread of questions, and the label the learner never had to write. */
class TutorConversationTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private static TutorConversation startedWith(String firstMessage) {
        return TutorConversation.start(USER_ID, firstMessage, "grammar", Instant.now());
    }

    @Nested
    class TheTitle {

        /** Derived, not asked for: nobody names a conversation before having it, and "Conversation 4" is unreadable. */
        @Test
        void comesFromTheFirstThingTheLearnerSaid() {
            assertThat(startedWith("  What is a gerund?  ").getTitle()).isEqualTo("What is a gerund?");
        }

        /** A pasted paragraph would otherwise put its whole second sentence in the list. */
        @Test
        void takesOnlyTheFirstLine() {
            assertThat(startedWith("Fix this sentence:\nHe go to school every day.").getTitle())
                    .isEqualTo("Fix this sentence:");
        }

        /** Cut on a word boundary: a title ending mid-word reads as a bug rather than as an abbreviation. */
        @Test
        void cutsALongOpeningWithoutBreakingAWord() {
            String longQuestion = "word ".repeat(80).strip();

            String title = startedWith(longQuestion).getTitle();

            assertThat(title).hasSizeLessThanOrEqualTo(200).endsWith("…").doesNotContain("wor…");
        }

        @Test
        void refusesToStartOnNothing() {
            assertThatThrownBy(() -> startedWith("   ")).isInstanceOf(BadRequestException.class)
                    .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("TUTOR_MESSAGE_EMPTY");
        }
    }

    @Nested
    class Activity {

        @Test
        void startsWithNothingInIt() {
            TutorConversation conversation = startedWith("What is a gerund?");

            assertThat(conversation.getMessageCount()).isZero();
            assertThat(conversation.isArchived()).isFalse();
        }

        /**
         * Both the question and the reply count. A learner shown "3 messages" over a thread of six would be reading a
         * different conversation from the one they had.
         */
        @Test
        void countsEveryTurn() {
            TutorConversation conversation = startedWith("What is a gerund?");

            conversation.recordTurn(Instant.now());
            conversation.recordTurn(Instant.now());

            assertThat(conversation.getMessageCount()).isEqualTo(2);
        }

        /** Kept so the list can sort by activity without reading a single message. */
        @Test
        void movesToTheTopOfTheListOnEachTurn() {
            TutorConversation conversation = startedWith("What is a gerund?");
            Instant later = Instant.now().plusSeconds(600);

            conversation.recordTurn(later);

            assertThat(conversation.getLastMessageAt()).isEqualTo(later);
        }
    }

    @Nested
    class Archiving {

        @Test
        void retiresAThread() {
            TutorConversation conversation = startedWith("What is a gerund?");

            conversation.archive(Instant.now());

            assertThat(conversation.isArchived()).isTrue();
        }

        /** An archived thread is a transcript, not somewhere to keep talking. */
        @Test
        void refusesANewTurnOnAnArchivedThread() {
            TutorConversation conversation = startedWith("What is a gerund?");
            conversation.archive(Instant.now());

            assertThatThrownBy(() -> conversation.recordTurn(Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("TUTOR_CONVERSATION_ARCHIVED");
        }

        @Test
        void refusesToArchiveTwice() {
            TutorConversation conversation = startedWith("What is a gerund?");
            conversation.archive(Instant.now());

            assertThatThrownBy(() -> conversation.archive(Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("TUTOR_CONVERSATION_ALREADY_ARCHIVED");
        }
    }
}
