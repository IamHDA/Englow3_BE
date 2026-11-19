package com.englow3.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.chat.dto.command.StartImageUploadCommand;
import com.englow3.chat.dto.result.ChatImageUploadTicket;
import com.englow3.chat.entity.ChatImageUpload;
import com.englow3.chat.repository.ChatImageUploadRepository;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.impl.ChatImageServiceImpl;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;

class ChatImageServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final ChatParticipantRepository participantRepo = mock(ChatParticipantRepository.class);
    private final ChatImageUploadRepository imageUploadRepo = mock(ChatImageUploadRepository.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ChatImageService service = new ChatImageServiceImpl(userDirectory, friendshipReader, participantRepo,
            imageUploadRepo, objectStorage, rateLimiter, transactions, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "chatBucket", "chat");
        ReflectionTestUtils.setField(service, "uploadUrlTtl", Duration.ofMinutes(10));
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        when(imageUploadRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStorage.presignPut(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://storage.example/put").toURL());
    }

    private StartImageUploadCommand command() {
        return new StartImageUploadCommand(conversationId, "image/png", 123_456);
    }

    @Nested
    class StartingAnUpload {

        private void callerIsFriendsWithTheOther() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));
            when(friendshipReader.areFriends(me, other)).thenReturn(true);
        }

        @Test
        void aPersonNotInTheConversationIsToldItDoesNotExist() {
            assertThatThrownBy(() -> service.startUpload(command())).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));

            verify(imageUploadRepo, never()).save(any());
            verify(objectStorage, never()).presignPut(anyString(), anyString(), anyString(), anyLong(), any());
        }

        @Test
        void aFormerFriendGetsNoUploadUrl() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.of(other));
            when(friendshipReader.areFriends(me, other)).thenReturn(false);

            assertThatThrownBy(() -> service.startUpload(command())).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));

            verify(imageUploadRepo, never()).save(any());
            verify(objectStorage, never()).presignPut(anyString(), anyString(), anyString(), anyLong(), any());
        }

        @Test
        void aConversationWhoseOtherPersonIsGoneGetsNoUploadUrl() {
            when(participantRepo.existsByIdConversationIdAndIdUserId(conversationId, me)).thenReturn(true);
            when(participantRepo.findOtherParticipant(conversationId, me)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.startUpload(command())).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));

            verify(friendshipReader, never()).areFriends(any(), any());
            verify(imageUploadRepo, never()).save(any());
        }

        @Test
        void aTypeOrSizeThatIsNotAcceptedIsRefusedBeforeAnythingElseIsAsked() {
            assertThatThrownBy(() -> service.startUpload(new StartImageUploadCommand(conversationId, "image/gif", 10)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_TYPE_NOT_ALLOWED"));

            verify(participantRepo, never()).existsByIdConversationIdAndIdUserId(any(), any());
        }

        @Test
        void everyStartCountsAgainstTheImageUploadRateLimit() {
            callerIsFriendsWithTheOther();

            service.startUpload(command());

            verify(rateLimiter).check(me, "chat-image-upload", 20, Duration.ofMinutes(10));
        }

        @Test
        void theKeyRecordedIsTheKeyThatWasSigned() {
            callerIsFriendsWithTheOther();

            ChatImageUploadTicket ticket = service.startUpload(command());

            ArgumentCaptor<ChatImageUpload> saved = ArgumentCaptor.forClass(ChatImageUpload.class);
            verify(imageUploadRepo).save(saved.capture());
            ArgumentCaptor<String> signedKey = ArgumentCaptor.forClass(String.class);
            verify(objectStorage).presignPut(eq("chat"), signedKey.capture(), eq("image/png"), eq(123_456L),
                    eq(Duration.ofMinutes(10)));
            assertThat(signedKey.getValue()).isEqualTo(saved.getValue().getObjectKey());
            assertThat(signedKey.getValue()).startsWith("conversations/" + conversationId + "/");
            assertThat(saved.getValue().getId()).isEqualTo(ticket.uploadId());
            assertThat(saved.getValue().getUploaderId()).isEqualTo(me);
            assertThat(saved.getValue().getConversationId()).isEqualTo(conversationId);
            assertThat(saved.getValue().getContentType()).isEqualTo("image/png");
            assertThat(saved.getValue().getContentLength()).isEqualTo(123_456);
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(now);
            assertThat(ticket.uploadUrl()).isEqualTo("https://storage.example/put");
            assertThat(ticket.contentType()).isEqualTo("image/png");
            assertThat(ticket.expiresInSeconds()).isEqualTo(600);
        }
    }

    @Nested
    class RemovingAbandonedUploads {

        private ChatImageUpload stale(String key) {
            return ChatImageUpload.issue(UUID.randomUUID(), conversationId, me, key, "image/png", 10,
                    now.minus(Duration.ofHours(30)));
        }

        @Test
        void looksOnlyAtUploadsOlderThanADay() {
            when(imageUploadRepo.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(any())).thenReturn(List.of());

            assertThat(service.removeAbandonedUploads()).isZero();

            verify(imageUploadRepo).findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(now.minus(Duration.ofHours(24)));
        }

        @Test
        void removesTheObjectOnlyOfARowThisCallActuallyDeleted() {
            ChatImageUpload mine = stale("conversations/c/mine.png");
            ChatImageUpload takenByAnother = stale("conversations/c/other.png");
            when(imageUploadRepo.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(mine, takenByAnother)).thenReturn(List.of());
            when(imageUploadRepo.deleteByIdReturningCount(mine.getId())).thenReturn(1);
            when(imageUploadRepo.deleteByIdReturningCount(takenByAnother.getId())).thenReturn(0);

            assertThat(service.removeAbandonedUploads()).isEqualTo(1);

            verify(objectStorage).delete("chat", "conversations/c/mine.png");
            verify(objectStorage, never()).delete("chat", "conversations/c/other.png");
        }

        @Test
        void anObjectThatWillNotGoDoesNotStopTheRest() {
            ChatImageUpload first = stale("conversations/c/1.png");
            ChatImageUpload second = stale("conversations/c/2.png");
            when(imageUploadRepo.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(first, second)).thenReturn(List.of());
            when(imageUploadRepo.deleteByIdReturningCount(any())).thenReturn(1);
            doAnswer(invocation -> {
                throw new IllegalStateException("store down");
            }).when(objectStorage).delete("chat", "conversations/c/1.png");

            assertThat(service.removeAbandonedUploads()).isEqualTo(2);

            verify(objectStorage).delete("chat", "conversations/c/2.png");
        }
    }
}
