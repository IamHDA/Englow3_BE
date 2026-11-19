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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.chat.dto.projection.ConversationRow;
import com.englow3.chat.dto.result.ConversationSummaryResult;
import com.englow3.chat.dto.result.OpenConversationOutcome;
import com.englow3.chat.entity.ChatConversation;
import com.englow3.chat.entity.ChatMessage;
import com.englow3.chat.query.ChatConversationListQuery;
import com.englow3.chat.repository.ChatConversationRepository;
import com.englow3.chat.repository.ChatMessageRepository;
import com.englow3.chat.repository.ChatMessageRepository.ImageKeyRow;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.impl.ChatConversationServiceImpl;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

class ChatConversationServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final ChatConversationRepository conversationRepo = mock(ChatConversationRepository.class);
    private final ChatParticipantRepository participantRepo = mock(ChatParticipantRepository.class);
    private final ChatMessageRepository messageRepo = mock(ChatMessageRepository.class);
    private final ChatConversationListQuery listQuery = mock(ChatConversationListQuery.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ChatConversationService service = new ChatConversationServiceImpl(userDirectory, friendshipReader,
            publicProfileReader, conversationRepo, participantRepo, messageRepo, listQuery, objectStorage, events,
            transactions, clock);

    private final AtomicBoolean inTransaction = new AtomicBoolean();

    private final UUID me = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "chatBucket", "chat");
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
    }

    private ChatConversation conversationWithNoMessages() {
        ChatConversation conversation = mock(ChatConversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getLastSeq()).thenReturn(0L);
        return conversation;
    }

    private ConversationRow row(UUID id, UUID otherUserId, long lastSeq, long lastReadSeq) {
        return new ConversationRow(id, lastSeq, now, lastReadSeq, otherUserId, lastSeq, otherUserId, "hello", false,
                false, now);
    }

    @Nested
    class Opening {

        private final String key = ChatConversation.directKey(me, friend);

        @BeforeEach
        void theFriendHasAProfile() {
            when(publicProfileReader.profiles(Set.of(friend)))
                    .thenReturn(Map.of(friend, new PublicProfile(friend, "Lan", "https://img.example/lan.png")));
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(0L));
        }

        @Test
        void aNonFriendIsRefusedAndNothingIsInserted() {
            when(friendshipReader.areFriends(me, friend)).thenReturn(false);

            assertThatThrownBy(() -> service.open(friend)).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));

            verify(conversationRepo, never()).insertIfAbsent(any(), anyString(), any());
            verify(participantRepo, never()).insertIfAbsent(any(), any(), any());
        }

        @Test
        void aConversationWithYourselfIsRefusedBeforeFriendshipIsAsked() {
            assertThatThrownBy(() -> service.open(me)).isInstanceOfSatisfying(BadRequestException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_SELF"));

            verifyNoInteractions(friendshipReader);
            verify(conversationRepo, never()).insertIfAbsent(any(), anyString(), any());
        }

        @Test
        void theFirstOpenCreatesTheConversationAndAddsBothPeople() {
            when(friendshipReader.areFriends(me, friend)).thenReturn(true);
            when(conversationRepo.insertIfAbsent(any(), eq(key), eq(now))).thenReturn(1);
            ChatConversation conversation = conversationWithNoMessages();
            when(conversationRepo.findByDirectKey(key)).thenReturn(Optional.of(conversation));

            OpenConversationOutcome outcome = service.open(friend);

            assertThat(outcome.created()).isTrue();
            verify(participantRepo).insertIfAbsent(conversationId, me, now);
            verify(participantRepo).insertIfAbsent(conversationId, friend, now);
            ConversationSummaryResult summary = outcome.conversation();
            assertThat(summary.id()).isEqualTo(conversationId);
            assertThat(summary.otherUser().userId()).isEqualTo(friend);
            assertThat(summary.otherUser().displayName()).isEqualTo("Lan");
            assertThat(summary.otherUser().avatarUrl()).isEqualTo("https://img.example/lan.png");
            assertThat(summary.canSend()).isTrue();
            assertThat(summary.lastMessage()).isNull();
            assertThat(summary.unreadCount()).isZero();
        }

        @Test
        void aSecondOpenFindsTheConversationAndAddsNobody() {
            when(friendshipReader.areFriends(me, friend)).thenReturn(true);
            when(conversationRepo.insertIfAbsent(any(), eq(key), eq(now))).thenReturn(0);
            ChatConversation conversation = conversationWithNoMessages();
            when(conversationRepo.findByDirectKey(key)).thenReturn(Optional.of(conversation));

            OpenConversationOutcome outcome = service.open(friend);

            assertThat(outcome.created()).isFalse();
            assertThat(outcome.conversation().id()).isEqualTo(conversationId);
            verify(participantRepo, never()).insertIfAbsent(any(), any(), any());
        }

        @Test
        void anExistingConversationShowsItsLastMessageAndTheCallersUnreadCount() {
            when(friendshipReader.areFriends(me, friend)).thenReturn(true);
            when(conversationRepo.insertIfAbsent(any(), eq(key), eq(now))).thenReturn(0);
            ChatConversation conversation = mock(ChatConversation.class);
            when(conversation.getId()).thenReturn(conversationId);
            when(conversation.getLastSeq()).thenReturn(4L);
            when(conversationRepo.findByDirectKey(key)).thenReturn(Optional.of(conversation));
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(1L));
            when(messageRepo.findByConversationIdAndSeqLessThanOrderBySeqDesc(eq(conversationId), anyLong(), any()))
                    .thenReturn(List.of(ChatMessage.send(conversationId, 4, friend, "c", "see you", null, now)));

            ConversationSummaryResult summary = service.open(friend).conversation();

            assertThat(summary.lastSeq()).isEqualTo(4);
            assertThat(summary.lastReadSeq()).isEqualTo(1);
            assertThat(summary.unreadCount()).isEqualTo(3);
            assertThat(summary.lastMessage().seq()).isEqualTo(4);
            assertThat(summary.lastMessage().preview()).isEqualTo("see you");
            assertThat(summary.lastMessage().senderId()).isEqualTo(friend);
        }
    }

    @Nested
    class Listing {

        @Test
        void asksForTheProfilesOfEveryoneOnThePageOnceAndSkipsAnErasedAccount() {
            UUID second = UUID.randomUUID();
            UUID erasedOther = null;
            UUID c1 = UUID.randomUUID();
            UUID c2 = UUID.randomUUID();
            UUID c3 = UUID.randomUUID();
            when(listQuery.list(me, 20, 0))
                    .thenReturn(List.of(row(c1, friend, 5, 2), row(c2, erasedOther, 3, 3), row(c3, second, 1, 0)));
            when(listQuery.count(me)).thenReturn(3L);
            when(publicProfileReader.profiles(Set.of(friend, second))).thenReturn(Map.of(friend,
                    new PublicProfile(friend, "Lan", null), second, new PublicProfile(second, "Minh", null)));

            Page<ConversationSummaryResult> page = service.list(PageRequest.of(0, 20));

            verify(publicProfileReader, times(1)).profiles(any());
            ArgumentCaptor<java.util.Collection<UUID>> asked = ArgumentCaptor.forClass(java.util.Collection.class);
            verify(publicProfileReader).profiles(asked.capture());
            assertThat(asked.getValue()).containsExactlyInAnyOrder(friend, second);
            assertThat(page.getContent()).extracting(ConversationSummaryResult::id).containsExactly(c1, c2, c3);
            assertThat(page.getContent().get(0).otherUser().displayName()).isEqualTo("Lan");
            assertThat(page.getContent().get(1).otherUser()).isNull();
            assertThat(page.getContent().get(2).otherUser().displayName()).isEqualTo("Minh");
        }

        @Test
        void countsUnreadAsLastSeqMinusMarkAndNeverAsksFriendshipPerConversation() {
            when(listQuery.list(me, 20, 0)).thenReturn(List.of(row(conversationId, friend, 5, 2)));
            when(listQuery.count(me)).thenReturn(1L);
            when(publicProfileReader.profiles(Set.of(friend)))
                    .thenReturn(Map.of(friend, new PublicProfile(friend, "Lan", null)));

            ConversationSummaryResult summary = service.list(PageRequest.of(0, 20)).getContent().get(0);

            assertThat(summary.unreadCount()).isEqualTo(3);
            assertThat(summary.lastSeq()).isEqualTo(5);
            assertThat(summary.lastReadSeq()).isEqualTo(2);
            assertThat(summary.canSend()).isNull();
            assertThat(summary.lastMessage().preview()).isEqualTo("hello");
            verifyNoInteractions(friendshipReader);
        }

        @Test
        void aListOfOnlyErasedAccountsAsksForNoProfiles() {
            when(listQuery.list(me, 20, 0)).thenReturn(List.of(row(conversationId, null, 2, 0)));
            when(listQuery.count(me)).thenReturn(1L);

            Page<ConversationSummaryResult> page = service.list(PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(1);
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void aSecondPageStartsWhereTheFirstEnded() {
            when(listQuery.list(me, 10, 10)).thenReturn(List.of());
            when(listQuery.count(me)).thenReturn(10L);

            Page<ConversationSummaryResult> page = service.list(PageRequest.of(1, 10));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(10);
        }
    }

    @Nested
    class Reading {

        @Test
        void aPersonNotInTheConversationIsToldItDoesNotExist() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(false);

            assertThatThrownBy(() -> service.get(conversationId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));
        }

        @Test
        void canSendFollowsWhetherTheTwoAreFriendsRightNow() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            ChatConversation conversation = conversationWithNoMessages();
            when(conversationRepo.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(friend));
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(0L));
            when(publicProfileReader.profiles(Set.of(friend)))
                    .thenReturn(Map.of(friend, new PublicProfile(friend, "Lan", null)));
            when(friendshipReader.areFriends(me, friend)).thenReturn(false);

            ConversationSummaryResult summary = service.get(conversationId);

            assertThat(summary.canSend()).isFalse();
            assertThat(summary.otherUser().displayName()).isEqualTo("Lan");
        }

        @Test
        void anErasedOtherPersonShowsAsNoUserAndCannotBeWrittenTo() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            ChatConversation conversation = conversationWithNoMessages();
            when(conversationRepo.findById(conversationId)).thenReturn(Optional.of(conversation));
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.empty());
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(0L));

            ConversationSummaryResult summary = service.get(conversationId);

            assertThat(summary.otherUser()).isNull();
            assertThat(summary.canSend()).isFalse();
            verifyNoInteractions(friendshipReader);
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void theUnreadBadgeCountsConversationsForTheCaller() {
            when(listQuery.unreadConversationCount(me)).thenReturn(4L);

            assertThat(service.unreadConversationCount()).isEqualTo(4);
        }
    }

    @Nested
    class Typing {

        @Test
        void theOtherPersonIsTheRecipientWhileTheyAreStillFriends() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(friend));
            when(friendshipReader.areFriends(me, friend)).thenReturn(true);

            assertThat(service.typingRecipient(me, conversationId)).contains(friend);
        }

        @Test
        void nobodyIsToldAfterUnfriending() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(friend));
            when(friendshipReader.areFriends(me, friend)).thenReturn(false);

            assertThat(service.typingRecipient(me, conversationId)).isEmpty();
        }

        @Test
        void aPersonNotInTheConversationGetsEmptyNotAnErrorAndNothingIsAsked() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(false);

            assertThat(service.typingRecipient(me, conversationId)).isEmpty();

            verify(participantRepo, never()).findOtherParticipant(any(), any());
            verifyNoInteractions(friendshipReader);
        }

        @Test
        void aConversationWhoseOtherPersonIsGoneHasNoRecipient() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.empty());

            assertThat(service.typingRecipient(me, conversationId)).isEmpty();

            verifyNoInteractions(friendshipReader);
        }
    }

    @Nested
    class MarkingRead {

        @Test
        void aNegativeSeqIsRefusedBeforeAnythingIsRead() {
            assertThatThrownBy(() -> service.markRead(conversationId, -1)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_READ_SEQ_INVALID"));

            verifyNoInteractions(participantRepo);
        }

        @Test
        void aPersonNotInTheConversationIsToldItDoesNotExistAndNothingIsWritten() {
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markRead(conversationId, 3)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));

            verify(participantRepo, never()).advanceReadMark(any(), any(), anyLong());
            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void publishesTheMarkNowStoredToTheOtherPersonWhenItMovedForward() {
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(2L));
            // Asked for 99, clamped to the last seq by the statement: the event carries what is stored, not the ask.
            when(participantRepo.advanceReadMark(conversationId, me, 99)).thenReturn(Optional.of(5L));
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(friend));

            service.markRead(conversationId, 99);

            verify(events).publishEvent(new ChatReadAdvanced(conversationId, me, 5, Set.of(friend)));
        }

        @Test
        void publishesNothingWhenTheMarkDidNotMove() {
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(3L));
            when(participantRepo.advanceReadMark(conversationId, me, 2)).thenReturn(Optional.of(3L));

            service.markRead(conversationId, 2);

            verify(events, never()).publishEvent(any(Object.class));
        }

        @Test
        void anErasedOtherPersonIsLeftOutOfTheRecipients() {
            when(participantRepo.findReadMark(conversationId, me)).thenReturn(Optional.of(0L));
            when(participantRepo.advanceReadMark(conversationId, me, 1)).thenReturn(Optional.of(1L));
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.empty());

            service.markRead(conversationId, 1);

            verify(events).publishEvent(new ChatReadAdvanced(conversationId, me, 1, Set.of()));
        }
    }

    @Nested
    class RemovingEmptyConversations {

        /** A row as the repository's projection hands it back. */
        private record Image(UUID getConversationId, String getImageObjectKey) implements ImageKeyRow {
        }

        @Test
        void deletesTheRowsInOneTransactionAndTheImagesAfterItEvenWhenOneObjectWillNotGo() {
            UUID empty = UUID.randomUUID();
            when(conversationRepo.findEmpty()).thenReturn(List.of(empty)).thenReturn(List.of());
            when(messageRepo.findImageKeys(List.of(empty))).thenReturn(
                    List.of(new Image(empty, "conversations/a/1.png"), new Image(empty, "conversations/a/2.png")));
            when(conversationRepo.deleteIfEmpty(List.of(empty))).thenReturn(List.of(empty));
            doAnswer(invocation -> {
                assertThat(inTransaction).as("objects are deleted after the transaction").isFalse();
                throw new IllegalStateException("store down");
            }).when(objectStorage).delete("chat", "conversations/a/1.png");

            int removed = service.removeEmptyConversations();

            assertThat(removed).isEqualTo(1);
            InOrder order = inOrder(messageRepo, conversationRepo, objectStorage);
            order.verify(messageRepo).findImageKeys(List.of(empty));
            order.verify(conversationRepo).deleteIfEmpty(List.of(empty));
            order.verify(objectStorage).delete("chat", "conversations/a/1.png");
            order.verify(objectStorage).delete("chat", "conversations/a/2.png");
        }

        @Test
        void onlyTheImagesOfConversationsThisSweepActuallyDeletedAreRemoved() {
            UUID mine = UUID.randomUUID();
            UUID takenByAnotherSweep = UUID.randomUUID();
            when(conversationRepo.findEmpty()).thenReturn(List.of(mine, takenByAnotherSweep)).thenReturn(List.of());
            when(messageRepo.findImageKeys(List.of(mine, takenByAnotherSweep)))
                    .thenReturn(List.of(new Image(mine, "conversations/mine/1.png"),
                            new Image(takenByAnotherSweep, "conversations/other/1.png")));
            when(conversationRepo.deleteIfEmpty(List.of(mine, takenByAnotherSweep))).thenReturn(List.of(mine));

            assertThat(service.removeEmptyConversations()).isEqualTo(1);

            verify(objectStorage).delete("chat", "conversations/mine/1.png");
            verify(objectStorage, never()).delete("chat", "conversations/other/1.png");
        }

        @Test
        void aSweepWhoseEveryCandidateWasTakenByAnotherStopsWithoutFailing() {
            UUID gone = UUID.randomUUID();
            when(conversationRepo.findEmpty()).thenReturn(List.of(gone));
            when(messageRepo.findImageKeys(List.of(gone)))
                    .thenReturn(List.of(new Image(gone, "conversations/x/1.png")));
            when(conversationRepo.deleteIfEmpty(List.of(gone))).thenReturn(List.of());

            assertThat(service.removeEmptyConversations()).isZero();

            verify(conversationRepo, times(1)).findEmpty();
            verifyNoInteractions(objectStorage);
        }

        @Test
        void keepsGoingUntilNothingIsLeft() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            when(conversationRepo.findEmpty()).thenReturn(List.of(first)).thenReturn(List.of(second))
                    .thenReturn(List.of());
            when(messageRepo.findImageKeys(any())).thenReturn(List.of());
            when(conversationRepo.deleteIfEmpty(List.of(first))).thenReturn(List.of(first));
            when(conversationRepo.deleteIfEmpty(List.of(second))).thenReturn(List.of(second));

            assertThat(service.removeEmptyConversations()).isEqualTo(2);

            verify(conversationRepo).deleteIfEmpty(List.of(first));
            verify(conversationRepo).deleteIfEmpty(List.of(second));
        }

        @Test
        void nothingToRemoveTouchesNeitherRowsNorObjects() {
            when(conversationRepo.findEmpty()).thenReturn(List.of());

            assertThat(service.removeEmptyConversations()).isZero();

            verify(conversationRepo, never()).deleteIfEmpty(any());
            verifyNoInteractions(objectStorage);
        }
    }
}
