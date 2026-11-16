package com.englow3.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.s3.model.S3Exception;

import com.englow3.chat.dto.command.SendMessageCommand;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.dto.result.SendMessageOutcome;
import com.englow3.chat.entity.ChatConversation;
import com.englow3.chat.entity.ChatImageUpload;
import com.englow3.chat.entity.ChatMessage;
import com.englow3.chat.repository.ChatConversationRepository;
import com.englow3.chat.repository.ChatImageUploadRepository;
import com.englow3.chat.repository.ChatMessageRepository;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.impl.ChatMessageServiceImpl;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.ObjectStorageClient.StoredObjectMetadata;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: what is asked in which order, what is locked, what is published and when the store is touched.
 * The rules inside {@link ChatMessage} have their own test, so nothing here stubs the entity to refuse something.
 */
class ChatMessageServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final ChatConversationRepository conversationRepo = mock(ChatConversationRepository.class);
    private final ChatParticipantRepository participantRepo = mock(ChatParticipantRepository.class);
    private final ChatMessageRepository messageRepo = mock(ChatMessageRepository.class);
    private final ChatImageUploadRepository imageUploadRepo = mock(ChatImageUploadRepository.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ChatMessageService service = new ChatMessageServiceImpl(userDirectory, friendshipReader,
            conversationRepo, participantRepo, messageRepo, imageUploadRepo, objectStorage, presignedUrls, rateLimiter,
            events, transactions, clock);

    /** True only while a callback given to the transaction template is running. */
    private final AtomicBoolean inTransaction = new AtomicBoolean();

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "chatBucket", "chat");
        ReflectionTestUtils.setField(service, "imageUrlTtl", Duration.ofHours(1));
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            inTransaction.set(true);
            try {
                return ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class));
            } finally {
                inTransaction.set(false);
            }
        });
        when(messageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // A listener waits for the commit of the transaction that published, so every event must be published inside
        // one: a publish from anywhere else fails the test that caused it.
        doAnswer(invocation -> {
            assertThat(inTransaction).as("events are published inside the transaction that wrote the change").isTrue();
            return null;
        }).when(events).publishEvent(any(Object.class));
    }

    private SendMessageCommand text(String body) {
        return new SendMessageCommand(conversationId, "client-1", body, null);
    }

    private void callerIsInTheConversation() {
        when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
    }

    private ChatConversation conversationLockedForSeq(long seq) {
        ChatConversation conversation = mock(ChatConversation.class);
        when(conversation.nextSeq(now)).thenReturn(seq);
        when(conversationRepo.findByIdForUpdate(conversationId)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private void theOtherPersonIsAFriend() {
        when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));
        when(friendshipReader.areFriends(me, other)).thenReturn(true);
    }

    private ChatMessage earlierMessage(UUID inConversation) {
        return ChatMessage.send(inConversation, 3, me, "client-1", "sent before", null, now.minusSeconds(60));
    }

    @Nested
    class Sending {

        @Test
        void aRetryOfASentMessageReturnsTheFirstOneAndChangesNothing() {
            ChatMessage first = earlierMessage(conversationId);
            when(messageRepo.findBySenderIdAndClientId(me, "client-1")).thenReturn(Optional.of(first));

            SendMessageOutcome outcome = service.send(text("sent before"));

            assertThat(outcome.created()).isFalse();
            assertThat(outcome.message().id()).isEqualTo(first.getId());
            assertThat(outcome.message().clientId()).isEqualTo("client-1");
            verify(conversationRepo, never()).findByIdForUpdate(any());
            verify(messageRepo, never()).save(any());
            verify(events, never()).publishEvent(any(Object.class));
            verify(transactions, times(1)).execute(any());
        }

        @Test
        void aClientIdAlreadyUsedInAnotherConversationIsRefused() {
            when(messageRepo.findBySenderIdAndClientId(me, "client-1"))
                    .thenReturn(Optional.of(earlierMessage(UUID.randomUUID())));

            assertThatThrownBy(() -> service.send(text("hi"))).isInstanceOfSatisfying(ConflictException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_CLIENT_ID_REUSED"));
            verify(messageRepo, never()).save(any());
        }

        @Test
        void aPersonNotInTheConversationIsToldItDoesNotExistAndNothingIsLocked() {
            assertThatThrownBy(() -> service.send(text("hi"))).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));

            verify(conversationRepo, never()).findByIdForUpdate(any());
            verify(messageRepo, never()).save(any());
        }

        @Test
        void everySendCountsAgainstTheRateLimit() {
            callerIsInTheConversation();
            conversationLockedForSeq(1);
            theOtherPersonIsAFriend();

            service.send(text("hi"));

            verify(rateLimiter).check(me, "chat-send", 60, Duration.ofMinutes(1));
        }

        @Test
        void anImageNotYetInTheBucketIsAConflictAndNoWriteTransactionIsOpened() {
            callerIsInTheConversation();
            UUID uploadId = UUID.randomUUID();
            ChatImageUpload upload = ChatImageUpload.issue(uploadId, conversationId, me, "conversations/c/u.png",
                    "image/png", 1_000, now);
            when(imageUploadRepo.findByIdAndUploaderIdAndConversationId(uploadId, me, conversationId))
                    .thenReturn(Optional.of(upload));
            when(objectStorage.metadata("chat", "conversations/c/u.png"))
                    .thenThrow(S3Exception.builder().statusCode(404).message("no such key").build());

            assertThatThrownBy(() -> service.send(new SendMessageCommand(conversationId, "client-1", null, uploadId)))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_NOT_UPLOADED"));

            verify(transactions, times(1)).execute(any());
            verify(conversationRepo, never()).findByIdForUpdate(any());
            verify(messageRepo, never()).save(any());
        }

        @Test
        void aStoreFailureThatIsNotAMissingObjectIsNotReportedAsAMissingImage() {
            callerIsInTheConversation();
            UUID uploadId = UUID.randomUUID();
            ChatImageUpload upload = ChatImageUpload.issue(uploadId, conversationId, me, "conversations/c/u.png",
                    "image/png", 1_000, now);
            when(imageUploadRepo.findByIdAndUploaderIdAndConversationId(uploadId, me, conversationId))
                    .thenReturn(Optional.of(upload));
            when(objectStorage.metadata(anyString(), anyString()))
                    .thenThrow(S3Exception.builder().statusCode(503).message("slow down").build());

            assertThatThrownBy(() -> service.send(new SendMessageCommand(conversationId, "client-1", null, uploadId)))
                    .isInstanceOf(S3Exception.class);
            verify(messageRepo, never()).save(any());
        }

        @Test
        void anUploadThatIsNotTheCallersOrNotThisConversationsIsNotFound() {
            callerIsInTheConversation();
            UUID uploadId = UUID.randomUUID();
            when(imageUploadRepo.findByIdAndUploaderIdAndConversationId(uploadId, me, conversationId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.send(new SendMessageCommand(conversationId, "client-1", "hi", uploadId)))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_UPLOAD_NOT_FOUND"));
            verify(objectStorage, never()).metadata(anyString(), anyString());
        }

        @Test
        void aFormerFriendCannotSendAndNothingIsSaved() {
            callerIsInTheConversation();
            conversationLockedForSeq(1);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));
            when(friendshipReader.areFriends(me, other)).thenReturn(false);

            assertThatThrownBy(() -> service.send(text("hi"))).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));

            verify(messageRepo, never()).save(any());
            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void aPersonWhoErasedTheirAccountCannotBeWrittenTo() {
            callerIsInTheConversation();
            conversationLockedForSeq(1);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.send(text("hi"))).isInstanceOfSatisfying(ConflictException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_PARTNER_GONE"));

            verify(friendshipReader, never()).areFriends(any(), any());
            verify(messageRepo, never()).save(any());
        }

        @Test
        void anUploadUsedOrSweptSinceItWasReadIsNotFoundAndNothingIsSaved() {
            callerIsInTheConversation();
            UUID uploadId = UUID.randomUUID();
            ChatImageUpload upload = ChatImageUpload.issue(uploadId, conversationId, me, "conversations/c/u.png",
                    "image/png", 1_000, now);
            when(imageUploadRepo.findByIdAndUploaderIdAndConversationId(uploadId, me, conversationId))
                    .thenReturn(Optional.of(upload));
            when(objectStorage.metadata("chat", "conversations/c/u.png"))
                    .thenReturn(new StoredObjectMetadata(1_000, "image/png"));
            conversationLockedForSeq(1);
            theOtherPersonIsAFriend();
            when(imageUploadRepo.deleteByIdReturningCount(uploadId)).thenReturn(0);

            assertThatThrownBy(() -> service.send(new SendMessageCommand(conversationId, "client-1", "hi", uploadId)))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_UPLOAD_NOT_FOUND"));

            verify(messageRepo, never()).save(any());
            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void locksTheConversationBeforeAskingFriendshipAndSavesThenMovesTheSendersMarkAndPublishesOnce() {
            callerIsInTheConversation();
            conversationLockedForSeq(7);
            theOtherPersonIsAFriend();

            SendMessageOutcome outcome = service.send(text("hello"));

            assertThat(outcome.created()).isTrue();
            ChatMessageResult sent = outcome.message();
            assertThat(sent.seq()).isEqualTo(7);
            assertThat(sent.body()).isEqualTo("hello");
            assertThat(sent.senderId()).isEqualTo(me);
            assertThat(sent.clientId()).isEqualTo("client-1");

            InOrder order = inOrder(conversationRepo, friendshipReader, messageRepo, participantRepo, events);
            order.verify(conversationRepo).findByIdForUpdate(conversationId);
            order.verify(friendshipReader).areFriends(me, other);
            order.verify(messageRepo).save(any(ChatMessage.class));
            order.verify(participantRepo).advanceReadMark(conversationId, me, 7L);
            order.verify(events).publishEvent(any(Object.class));

            ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
            verify(events, times(1)).publishEvent(published.capture());
            assertThat(published.getValue())
                    .isEqualTo(new ChatMessageSent(conversationId, sent.id(), 7, me, Set.of(other)));
        }

        @Test
        void anImageIsCheckedOutsideTransactionsThenConsumedAndLinkedToTheMessage() {
            callerIsInTheConversation();
            UUID uploadId = UUID.randomUUID();
            String key = "conversations/c/u.png";
            ChatImageUpload upload = ChatImageUpload.issue(uploadId, conversationId, me, key, "image/png", 1_000, now);
            when(imageUploadRepo.findByIdAndUploaderIdAndConversationId(uploadId, me, conversationId))
                    .thenReturn(Optional.of(upload));
            doAnswer(invocation -> {
                assertThat(inTransaction).as("the store is never asked inside a transaction").isFalse();
                return new StoredObjectMetadata(1_000, "image/png");
            }).when(objectStorage).metadata("chat", key);
            when(imageUploadRepo.deleteByIdReturningCount(uploadId)).thenReturn(1);
            conversationLockedForSeq(2);
            theOtherPersonIsAFriend();
            when(presignedUrls.resolve("chat", key, Duration.ofHours(1))).thenReturn("https://storage.example/get");

            SendMessageOutcome outcome = service
                    .send(new SendMessageCommand(conversationId, "client-1", null, uploadId));

            ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
            verify(messageRepo).save(saved.capture());
            assertThat(saved.getValue().getImageObjectKey()).isEqualTo(key);
            assertThat(saved.getValue().getBody()).isNull();
            verify(imageUploadRepo).deleteByIdReturningCount(uploadId);
            assertThat(outcome.message().imageUrl()).isEqualTo("https://storage.example/get");
        }

        @Test
        void twoRequestsWithOneClientIdAtOnceSendOneMessage() {
            callerIsInTheConversation();
            conversationLockedForSeq(5);
            ChatMessage winner = earlierMessage(conversationId);
            // The first check (before the lock) found nothing; by the time this request has the lock the other has
            // committed.
            when(messageRepo.findBySenderIdAndClientId(me, "client-1")).thenReturn(Optional.empty(),
                    Optional.of(winner));

            SendMessageOutcome outcome = service.send(text("hi"));

            assertThat(outcome.created()).isFalse();
            assertThat(outcome.message().id()).isEqualTo(winner.getId());
            verify(messageRepo, never()).save(any());
            verify(events, never()).publishEvent(any(Object.class));
        }
    }

    @Nested
    class History {

        private ChatMessage message(long seq, UUID sender, String clientId) {
            return ChatMessage.send(conversationId, seq, sender, clientId, "message " + seq, null, now);
        }

        @Test
        void askingAfterAndBeforeAtOnceIsRefused() {
            assertThatThrownBy(() -> service.history(conversationId, 3L, 9L, 50)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_HISTORY_CURSOR_CONFLICT"));
        }

        @Test
        void aPersonNotInTheConversationIsToldItDoesNotExist() {
            assertThatThrownBy(() -> service.history(conversationId, null, null, 50)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));
            verify(messageRepo, never()).findByConversationIdAndSeqLessThanOrderBySeqDesc(any(), anyLong(), any());
        }

        @Test
        void aLimitOfAThousandIsClampedToOneHundred() {
            callerIsInTheConversation();
            when(messageRepo.findByConversationIdAndSeqLessThanOrderBySeqDesc(eq(conversationId), anyLong(), any()))
                    .thenReturn(List.of());

            service.history(conversationId, null, null, 1000);

            ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(messageRepo).findByConversationIdAndSeqLessThanOrderBySeqDesc(eq(conversationId), eq(Long.MAX_VALUE),
                    page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        void aLimitBelowOneIsRaisedToOne() {
            callerIsInTheConversation();
            when(messageRepo.findByConversationIdAndSeqLessThanOrderBySeqDesc(eq(conversationId), anyLong(), any()))
                    .thenReturn(List.of());

            service.history(conversationId, null, null, 0);

            ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(messageRepo).findByConversationIdAndSeqLessThanOrderBySeqDesc(eq(conversationId), eq(Long.MAX_VALUE),
                    page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(1);
        }

        @Test
        void scrollingBackFetchesNewestFirstButAnswersInAscendingSeq() {
            callerIsInTheConversation();
            when(messageRepo.findByConversationIdAndSeqLessThanOrderBySeqDesc(eq(conversationId), eq(9L), any()))
                    .thenReturn(List.of(message(8, other, "o-8"), message(7, me, "m-7"), message(6, other, "o-6")));

            List<ChatMessageResult> history = service.history(conversationId, null, 9L, 3);

            assertThat(history).extracting(ChatMessageResult::seq).containsExactly(6L, 7L, 8L);
        }

        @Test
        void catchingUpFetchesOldestFirstAfterTheGivenSeq() {
            callerIsInTheConversation();
            when(messageRepo.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(eq(conversationId), eq(5L), any()))
                    .thenReturn(List.of(message(6, other, "o-6"), message(7, me, "m-7")));

            List<ChatMessageResult> history = service.history(conversationId, 5L, null, 50);

            assertThat(history).extracting(ChatMessageResult::seq).containsExactly(6L, 7L);
            verify(messageRepo, never()).findByConversationIdAndSeqLessThanOrderBySeqDesc(any(), anyLong(), any());
        }

        @Test
        void theClientIdComesBackOnlyOnTheCallersOwnMessages() {
            callerIsInTheConversation();
            when(messageRepo.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(eq(conversationId), eq(0L), any()))
                    .thenReturn(List.of(message(1, other, "o-1"), message(2, me, "m-2")));

            List<ChatMessageResult> history = service.history(conversationId, 0L, null, 50);

            assertThat(history).extracting(ChatMessageResult::clientId).containsExactly(null, "m-2");
        }
    }

    @Nested
    class Recalling {

        private final UUID messageId = UUID.randomUUID();

        @Test
        void aMessageThatDoesNotExistIsNotFound() {
            when(messageRepo.findByIdForUpdate(messageId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.recall(messageId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_FOUND"));
        }

        @Test
        void aMessageOfAConversationTheCallerIsNotInLooksTheSame() {
            ChatMessage message = mock(ChatMessage.class);
            when(message.getConversationId()).thenReturn(conversationId);
            when(messageRepo.findByIdForUpdate(messageId)).thenReturn(Optional.of(message));
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(false);

            assertThatThrownBy(() -> service.recall(messageId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_FOUND"));
            verify(message, never()).recall(any(), any());
        }

        @Test
        void recallsAsTheCallerPublishesInsideTheTransactionAndDeletesTheImageAfterIt() {
            ChatMessage message = mock(ChatMessage.class);
            when(message.getConversationId()).thenReturn(conversationId);
            when(message.getSeq()).thenReturn(4L);
            when(message.recall(me, now)).thenReturn("conversations/c/u.png");
            when(messageRepo.findByIdForUpdate(messageId)).thenReturn(Optional.of(message));
            callerIsInTheConversation();
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));
            doAnswer(invocation -> {
                assertThat(inTransaction).as("the object is deleted after the transaction").isFalse();
                return null;
            }).when(objectStorage).delete("chat", "conversations/c/u.png");

            service.recall(messageId);

            verify(message).recall(me, now);
            // Only the locked lookup: a message loaded any other way first would not be refreshed after the lock.
            verify(messageRepo, never()).findById(any());
            verify(events).publishEvent(new ChatMessageRecalled(conversationId, messageId, 4, Set.of(other)));
            InOrder order = inOrder(transactions, objectStorage);
            order.verify(transactions).execute(any());
            order.verify(objectStorage).delete("chat", "conversations/c/u.png");
        }

        @Test
        void aMessageWithoutAnImageDeletesNothing() {
            ChatMessage message = mock(ChatMessage.class);
            when(message.getConversationId()).thenReturn(conversationId);
            when(message.recall(me, now)).thenReturn(null);
            when(messageRepo.findByIdForUpdate(messageId)).thenReturn(Optional.of(message));
            callerIsInTheConversation();
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));

            service.recall(messageId);

            verify(objectStorage, never()).delete(anyString(), anyString());
        }

        @Test
        void aFailureToDeleteTheObjectDoesNotUndoTheRecall() {
            ChatMessage message = mock(ChatMessage.class);
            when(message.getConversationId()).thenReturn(conversationId);
            when(message.recall(me, now)).thenReturn("conversations/c/u.png");
            when(messageRepo.findByIdForUpdate(messageId)).thenReturn(Optional.of(message));
            callerIsInTheConversation();
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));
            doAnswer(invocation -> {
                throw new IllegalStateException("store down");
            }).when(objectStorage).delete(anyString(), anyString());

            service.recall(messageId);

            verify(events).publishEvent(any(ChatMessageRecalled.class));
        }

        @Test
        void aConversationWhoseOtherPersonIsGoneStillRecallsAndTellsNobody() {
            ChatMessage message = mock(ChatMessage.class);
            when(message.getConversationId()).thenReturn(conversationId);
            when(message.getSeq()).thenReturn(4L);
            when(messageRepo.findByIdForUpdate(messageId)).thenReturn(Optional.of(message));
            callerIsInTheConversation();
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.empty());

            service.recall(messageId);

            verify(events).publishEvent(new ChatMessageRecalled(conversationId, messageId, 4, Set.of()));
        }
    }
}
