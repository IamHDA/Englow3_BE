package com.englow3.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.tutor.dto.command.ReportTutorMessageCommand;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.entity.TutorConversation;
import com.englow3.tutor.entity.TutorMessage;
import com.englow3.tutor.entity.TutorMessageStatus;
import com.englow3.tutor.repository.TutorConversationRepository;
import com.englow3.tutor.repository.TutorMessageRepository;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Asking a question. What has to be true before anyone is charged for an answer, and whose conversation a learner is
 * allowed to read.
 */
class TutorServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    private static final int DAILY_LIMIT = 5;

    private final TutorConversationRepository conversationRepo = mock(TutorConversationRepository.class);
    private final TutorMessageRepository messageRepo = mock(TutorMessageRepository.class);
    private final AiJobQueue aiJobQueue = mock(AiJobQueue.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);

    private final TutorService service = new com.englow3.tutor.service.impl.TutorServiceImpl(conversationRepo,
            messageRepo, aiJobQueue, userDirectory, new ObjectMapper(), CLOCK);

    private final UUID userId = UUID.randomUUID();
    private TutorConversation conversation;

    @BeforeEach
    void setUp() {
        conversation = TutorConversation.start(userId, "What is a gerund?", "grammar", Instant.now());

        when(userDirectory.requireCurrentUserId()).thenReturn(userId);
        when(conversationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepo.findByIdAndUserId(conversation.getId(), userId)).thenReturn(Optional.of(conversation));
        when(messageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepo.findByTutorConversationIdOrderByOrderNo(any())).thenReturn(List.of());
        // The budget is the queue's to count now; by default there is room left.
        when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(true);
        when(aiJobQueue.dailyRequestLimit()).thenReturn(DAILY_LIMIT);
    }

    private List<TutorMessage> savedMessages() {
        ArgumentCaptor<TutorMessage> saved = ArgumentCaptor.forClass(TutorMessage.class);
        verify(messageRepo, org.mockito.Mockito.atLeastOnce()).save(saved.capture());

        return saved.getAllValues();
    }

    @Nested
    class Asking {

        @Test
        void opensAThreadOnTheFirstQuestion() {
            var result = service.send(new SendTutorMessageCommand(null, "What is a gerund?", "grammar"));

            assertThat(result.conversation().title()).isEqualTo("What is a gerund?");
            verify(conversationRepo).save(any());
        }

        /**
         * The question is written before anything is asked of a provider. A question lost because the provider was down
         * is one the learner has to type again, with no way of knowing that is what happened.
         */
        @Test
        void storesTheQuestionAndAPlaceForTheAnswer() {
            service.send(new SendTutorMessageCommand(null, "What is a gerund?", null));

            List<TutorMessage> saved = savedMessages();
            assertThat(saved).hasSize(2);
            assertThat(saved.get(0).getContent()).isEqualTo("What is a gerund?");
            assertThat(saved.get(0).getStatus()).isEqualTo(TutorMessageStatus.READY);
            assertThat(saved.get(1).getStatus()).isEqualTo(TutorMessageStatus.PENDING);
            assertThat(saved.get(1).getContent()).isNull();
        }

        /** The reply is queued against the pending turn, which is what the screen then polls. */
        @Test
        void queuesTheAnswerAgainstThePendingTurn() {
            service.send(new SendTutorMessageCommand(null, "What is a gerund?", null));

            UUID pendingId = savedMessages().get(1).getId();
            verify(aiJobQueue).enqueueTutorReply(eq(pendingId), anyString(), anyString(), eq(TutorPrompt.VERSION),
                    eq(userId));
        }

        /** Continuing a thread numbers the new turns after the ones already in it, not from one. */
        @Test
        void numbersNewTurnsAfterTheExistingOnes() {
            TutorMessage answered = TutorMessage.awaitingReply(conversation.getId(), 2);
            answered.answer("A verb used as a noun.", "m", "v1", 1, 1, Instant.now());
            when(messageRepo.findByTutorConversationIdOrderByOrderNo(conversation.getId()))
                    .thenReturn(List.of(TutorMessage.fromLearner(conversation.getId(), 1, "earlier"), answered));

            service.send(new SendTutorMessageCommand(conversation.getId(), "Give me an example.", null));

            assertThat(savedMessages()).extracting(TutorMessage::getOrderNo).containsExactly(3, 4);
        }

        /** A second question before the first is answered would be generated without that answer, and paid twice. */
        @Test
        void refusesANewQuestionWhileTheLastIsUnanswered() {
            when(messageRepo.findByTutorConversationIdOrderByOrderNo(conversation.getId()))
                    .thenReturn(List.of(TutorMessage.fromLearner(conversation.getId(), 1, "earlier"),
                            TutorMessage.awaitingReply(conversation.getId(), 2)));

            assertThatThrownBy(
                    () -> service.send(new SendTutorMessageCommand(conversation.getId(), "And another?", null)))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("TUTOR_REPLY_PENDING");
            verify(messageRepo, never()).save(any());
        }

        @Test
        void refusesAnArchivedConversation() {
            conversation.archive(Instant.now());

            assertThatThrownBy(
                    () -> service.send(new SendTutorMessageCommand(conversation.getId(), "Still there?", null)))
                            .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                            .isEqualTo("TUTOR_CONVERSATION_ARCHIVED");
            verify(aiJobQueue, never()).enqueueTutorReply(any(), anyString(), anyString(), anyString(), any());
        }

        /** The earlier turns travel with the question, or the tutor answers a follow-up it cannot see the start of. */
        @Test
        void sendsTheEarlierTurnsWithTheQuestion() {
            TutorMessage earlier = TutorMessage.awaitingReply(conversation.getId(), 2);
            earlier.answer("A verb used as a noun.", "m", "v1", 1, 1, Instant.now());
            when(messageRepo.findByTutorConversationIdOrderByOrderNo(conversation.getId())).thenReturn(
                    List.of(TutorMessage.fromLearner(conversation.getId(), 1, "What is a gerund?"), earlier));

            service.send(new SendTutorMessageCommand(conversation.getId(), "Give me an example.", null));

            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(aiJobQueue).enqueueTutorReply(any(), payload.capture(), anyString(), anyString(), any());
            assertThat(payload.getValue()).contains("What is a gerund?").contains("A verb used as a noun.")
                    .contains("Give me an example.");
        }

        /** Both turns count towards the size the list shows. */
        @Test
        void countsBothTurnsOnTheThread() {
            when(messageRepo.findByTutorConversationIdOrderByOrderNo(conversation.getId())).thenReturn(List.of());

            var result = service.send(new SendTutorMessageCommand(conversation.getId(), "Give me an example.", null));

            assertThat(result.conversation().messageCount()).isEqualTo(2);
        }
    }

    @Nested
    class TheDailyCeiling {

        /**
         * Checked before the question is stored, unlike speaking's, which is checked at submission. A recording is
         * effort already spent, so refusing it late refuses work already done; typing a question again costs a moment,
         * and refusing before the thread grows a turn that will never be answered is the kinder failure.
         */
        @Test
        void refusesOnceTodaysQuestionsAreSpent() {
            when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(false);

            assertThatThrownBy(() -> service.send(new SendTutorMessageCommand(null, "What is a gerund?", null)))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("TUTOR_DAILY_LIMIT_REACHED");
        }

        /** Nothing is written on the way to the refusal - no thread, no turn, no job. */
        @Test
        void leavesNothingBehindWhenItRefuses() {
            when(aiJobQueue.hasDailyAllowance(userId)).thenReturn(false);

            assertThatThrownBy(() -> service.send(new SendTutorMessageCommand(null, "What is a gerund?", null)))
                    .isInstanceOf(ConflictException.class);

            verify(conversationRepo, never()).save(any());
            verify(messageRepo, never()).save(any());
            verify(aiJobQueue, never()).enqueueTutorReply(any(), anyString(), anyString(), anyString(), any());
        }
    }

    @Nested
    class WhoMayRead {

        /**
         * Someone else's conversation answers exactly as one that does not exist. A different answer for each would
         * make a conversation id a way to find out whose threads are there.
         */
        @Test
        void refusesToReadAThreadBelongingToSomebodyElse() {
            UUID other = UUID.randomUUID();
            when(conversationRepo.findByIdAndUserId(other, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.conversation(other)).isInstanceOf(NotFoundException.class)
                    .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("TUTOR_CONVERSATION_NOT_FOUND");
        }

        @Test
        void refusesToPostIntoAThreadBelongingToSomebodyElse() {
            UUID other = UUID.randomUUID();
            when(conversationRepo.findByIdAndUserId(other, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.send(new SendTutorMessageCommand(other, "hello", null)))
                    .isInstanceOf(NotFoundException.class);

            verify(aiJobQueue, never()).enqueueTutorReply(any(), anyString(), anyString(), anyString(), any());
        }

        @Test
        void refusesToArchiveAThreadBelongingToSomebodyElse() {
            UUID other = UUID.randomUUID();
            when(conversationRepo.findByIdAndUserId(other, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archive(other)).isInstanceOf(NotFoundException.class);
        }

        /** The ownership check runs before the message is looked up, so a message id leaks nothing either. */
        @Test
        void refusesToReportIntoAThreadBelongingToSomebodyElse() {
            UUID other = UUID.randomUUID();
            when(conversationRepo.findByIdAndUserId(other, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.report(other, UUID.randomUUID(), new ReportTutorMessageCommand("wrong")))
                    .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                    .isEqualTo("TUTOR_CONVERSATION_NOT_FOUND");

            verify(messageRepo, never()).findByIdAndTutorConversationId(any(), any());
        }
    }

    @Nested
    class Reporting {

        private TutorMessage answeredTurn() {
            TutorMessage message = TutorMessage.awaitingReply(conversation.getId(), 2);
            message.answer("A gerund is a verb used as a noun.", "gpt-4o-mini", "v1", 10, 20, Instant.now());
            when(messageRepo.findByIdAndTutorConversationId(message.getId(), conversation.getId()))
                    .thenReturn(Optional.of(message));
            return message;
        }

        @Test
        void marksTheAnswerAsReported() {
            TutorMessage message = answeredTurn();

            var result = service.report(conversation.getId(), message.getId(), new ReportTutorMessageCommand("wrong"));

            assertThat(result.reported()).isTrue();
            assertThat(result.content()).isEqualTo("A gerund is a verb used as a noun.");
        }

        @Test
        void acceptsAReportWithNoBody() {
            TutorMessage message = answeredTurn();

            assertThat(service.report(conversation.getId(), message.getId(), null).reported()).isTrue();
        }

        @Test
        void refusesAMessageThatIsNotInThisThread() {
            UUID unknown = UUID.randomUUID();
            when(messageRepo.findByIdAndTutorConversationId(unknown, conversation.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> service.report(conversation.getId(), unknown, new ReportTutorMessageCommand("wrong")))
                            .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                            .isEqualTo("TUTOR_MESSAGE_NOT_FOUND");
        }
    }
}
