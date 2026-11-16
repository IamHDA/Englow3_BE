package com.englow3.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.chat.dto.command.SendMessageCommand;
import com.englow3.chat.dto.command.StartImageUploadCommand;
import com.englow3.chat.dto.result.ChatImageUploadTicket;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.dto.result.ConversationSummaryResult;
import com.englow3.chat.dto.result.OpenConversationOutcome;
import com.englow3.chat.dto.result.SendMessageOutcome;
import com.englow3.chat.entity.ChatMessage;
import com.englow3.chat.repository.ChatConversationRepository;
import com.englow3.chat.repository.ChatMessageRepository;
import com.englow3.chat.repository.ChatParticipantRepository;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;
import com.englow3.chat.service.ChatMessageRecalled;
import com.englow3.chat.service.ChatMessageSent;
import com.englow3.chat.service.ChatMessageService;
import com.englow3.chat.service.ChatReadAdvanced;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.ObjectStorageClient.StoredObjectMetadata;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * What a mock cannot show: the unique key that makes a pair one conversation, the row lock that makes seq order commit
 * order, the {@code greatest(...)} read mark, the check constraints, the hand-written list query, and what erasing an
 * account does to conversations. Object storage is a mock - nothing here has a bucket - so the tests assert which keys
 * would be signed, checked and deleted. Each test makes its own learners, so tests sharing the container never see each
 * other's rows.
 */
@RecordApplicationEvents
class ChatIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    ChatConversationService conversationService;
    @Autowired
    ChatMessageService messageService;
    @Autowired
    ChatImageService imageService;
    @Autowired
    ChatParticipantRepository participantRepo;
    @Autowired
    ChatConversationRepository conversationRepo;
    @Autowired
    ChatMessageRepository messageRepo;
    @Autowired
    TransactionTemplate transactions;
    @Autowired
    ApplicationEvents events;

    @MockitoBean
    ObjectStorageClient objectStorage;

    private LearnerFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LearnerFixture(jdbc);
        when(objectStorage.presignPut(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://storage.example/put").toURL());
        when(objectStorage.presignGet(anyString(), anyString(), any())).thenAnswer(
                invocation -> URI.create("https://storage.example/get/" + invocation.getArgument(1)).toURL());
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private void befriend(UUID a, UUID b) {
        jdbc.sql("""
                insert into friendships (id, requester_id, addressee_id, status, accepted_at)
                values (:id, :a, :b, 'ACCEPTED', now())
                """).param("id", UUID.randomUUID()).param("a", a).param("b", b).update();
    }

    private void unfriend(UUID a, UUID b) {
        jdbc.sql("""
                delete from friendships
                where (requester_id = :a and addressee_id = :b) or (requester_id = :b and addressee_id = :a)
                """).param("a", a).param("b", b).update();
    }

    private OpenConversationOutcome open(UUID as, UUID with) {
        SignedIn.as(jdbc, as);
        return conversationService.open(with);
    }

    private UUID conversationOf(UUID a, UUID b) {
        return open(a, b).conversation().id();
    }

    private SendMessageOutcome send(UUID as, UUID conversation, String body) {
        return send(as, conversation, UUID.randomUUID().toString(), body);
    }

    private SendMessageOutcome send(UUID as, UUID conversation, String clientId, String body) {
        SignedIn.as(jdbc, as);
        return messageService.send(new SendMessageCommand(conversation, clientId, body, null));
    }

    private long lastSeq(UUID conversation) {
        return jdbc.sql("select last_seq from chat_conversations where id = :c").param("c", conversation)
                .query(Long.class).single();
    }

    private long readMark(UUID conversation, UUID user) {
        return jdbc.sql("select last_read_seq from chat_participants where conversation_id = :c and user_id = :u")
                .param("c", conversation).param("u", user).query(Long.class).single();
    }

    private long count(String sql, Object... namedParams) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        for (int i = 0; i < namedParams.length; i += 2) {
            spec = spec.param((String) namedParams[i], namedParams[i + 1]);
        }
        return spec.query(Long.class).single();
    }

    private ChatImageUploadTicket startUpload(UUID as, UUID conversation) {
        SignedIn.as(jdbc, as);
        return imageService.startUpload(new StartImageUploadCommand(conversation, "image/png", 1_000));
    }

    private String keyOf(UUID uploadId) {
        return jdbc.sql("select object_key from chat_image_uploads where id = :id").param("id", uploadId)
                .query(String.class).single();
    }

    private SendMessageOutcome sendImage(UUID as, UUID conversation, UUID uploadId, String body) {
        SignedIn.as(jdbc, as);
        return messageService.send(new SendMessageCommand(conversation, UUID.randomUUID().toString(), body, uploadId));
    }

    // --- one conversation per pair ---------------------------------------------------------------------------------

    @Test
    void twoFriendsOpeningAChatAtTheSameMomentEndUpInOneConversationWithTwoParticipants() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<OpenConversationOutcome> fromA = pool.submit(() -> openAfter(go, a, b));
            Future<OpenConversationOutcome> fromB = pool.submit(() -> openAfter(go, b, a));
            go.countDown();

            OpenConversationOutcome outcomeA = fromA.get(30, TimeUnit.SECONDS);
            OpenConversationOutcome outcomeB = fromB.get(30, TimeUnit.SECONDS);

            assertThat(List.of(outcomeA.created(), outcomeB.created())).containsExactlyInAnyOrder(true, false);
            assertThat(outcomeA.conversation().id()).isEqualTo(outcomeB.conversation().id());
            assertThat(
                    count("select count(*) from chat_conversations where id = :c", "c", outcomeA.conversation().id()))
                            .isEqualTo(1);
            assertThat(count("select count(*) from chat_participants where conversation_id = :c", "c",
                    outcomeA.conversation().id())).isEqualTo(2);
            assertThat(outcomeA.conversation().otherUser().userId()).isEqualTo(b);
            assertThat(outcomeB.conversation().otherUser().userId()).isEqualTo(a);
        } finally {
            pool.shutdownNow();
        }
    }

    private OpenConversationOutcome openAfter(CountDownLatch go, UUID as, UUID with) throws Exception {
        go.await();
        try {
            return open(as, with);
        } finally {
            SignedIn.out();
        }
    }

    @Test
    void openingAgainReturnsTheSameConversationAndOnlyFriendsMayOpenOne() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        UUID stranger = fixture.learner();
        befriend(a, b);

        OpenConversationOutcome first = open(a, b);
        OpenConversationOutcome again = open(a, b);
        OpenConversationOutcome fromTheOtherSide = open(b, a);

        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(fromTheOtherSide.created()).isFalse();
        assertThat(again.conversation().id()).isEqualTo(first.conversation().id());
        assertThat(fromTheOtherSide.conversation().id()).isEqualTo(first.conversation().id());
        assertThat(first.conversation().canSend()).isTrue();
        assertThatThrownBy(() -> open(a, stranger)).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));
    }

    // --- seq, read marks -------------------------------------------------------------------------------------------

    @Test
    void threeMessagesGetSeqOneTwoThreeAndOnlyTheSendersMarkMoves() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);

        List<Long> seqs = new ArrayList<>();
        for (String body : List.of("one", "two", "three")) {
            seqs.add(send(a, conversation, body).message().seq());
        }

        assertThat(seqs).containsExactly(1L, 2L, 3L);
        assertThat(lastSeq(conversation)).isEqualTo(3);
        assertThat(readMark(conversation, a)).isEqualTo(3);
        assertThat(readMark(conversation, b)).isZero();
        assertThat(events.stream(ChatMessageSent.class).toList()).hasSize(3).allSatisfy(sent -> {
            assertThat(sent.conversationId()).isEqualTo(conversation);
            assertThat(sent.senderId()).isEqualTo(a);
            assertThat(sent.recipientIds()).containsExactly(b);
        });

        SignedIn.as(jdbc, b);
        ConversationSummaryResult asSeenByB = conversationService.get(conversation);
        assertThat(asSeenByB.unreadCount()).isEqualTo(3);
        assertThat(asSeenByB.lastMessage().preview()).isEqualTo("three");
        assertThat(asSeenByB.lastMessage().senderId()).isEqualTo(a);
    }

    @Test
    void theReadMarkNeverMovesBackAndIsClampedToTheLastSeq() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        for (String body : List.of("one", "two", "three")) {
            send(a, conversation, body);
        }

        assertThat(advance(conversation, a, 2)).contains(3L);
        assertThat(readMark(conversation, a)).isEqualTo(3);
        assertThat(advance(conversation, b, 1)).contains(1L);
        assertThat(advance(conversation, b, 99)).as("clamped to last_seq").contains(3L);
        assertThat(advance(conversation, b, 0)).contains(3L);
        assertThat(advance(conversation, UUID.randomUUID(), 2)).as("not in the conversation").isEmpty();
    }

    private Optional<Long> advance(UUID conversation, UUID user, long seq) {
        // A statement that writes must run in the caller's transaction; the service always has one.
        return transactions.execute(tx -> participantRepo.advanceReadMark(conversation, user, seq));
    }

    @Test
    void markingReadMovesTheMarkAnnouncesItOnceAndOnlyWhenItMoved() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        for (String body : List.of("one", "two", "three")) {
            send(a, conversation, body);
        }
        SignedIn.as(jdbc, b);

        conversationService.markRead(conversation, 2);
        conversationService.markRead(conversation, 1);
        conversationService.markRead(conversation, 2);
        conversationService.markRead(conversation, 99);

        assertThat(readMark(conversation, b)).isEqualTo(3);
        assertThat(events.stream(ChatReadAdvanced.class).toList()).extracting(ChatReadAdvanced::upToSeq)
                .containsExactly(2L, 3L);
        assertThat(events.stream(ChatReadAdvanced.class).toList()).allSatisfy(advanced -> {
            assertThat(advanced.readerId()).isEqualTo(b);
            assertThat(advanced.recipientIds()).containsExactly(a);
        });
        assertThat(conversationService.get(conversation).unreadCount()).isZero();
        assertThatThrownBy(() -> conversationService.markRead(UUID.randomUUID(), 1)).isInstanceOfSatisfying(
                NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void sendsFromBothSidesAtOnceTakeConsecutiveSeqsWithNoGapsOrDuplicates() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<?> fromA = pool.submit(() -> sendMany(go, a, conversation, "a"));
            Future<?> fromB = pool.submit(() -> sendMany(go, b, conversation, "b"));
            go.countDown();
            fromA.get(60, TimeUnit.SECONDS);
            fromB.get(60, TimeUnit.SECONDS);

            List<Long> seqs = jdbc.sql("select seq from chat_messages where conversation_id = :c order by seq")
                    .param("c", conversation).query(Long.class).list();
            assertThat(seqs).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
            assertThat(lastSeq(conversation)).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }
    }

    private Object sendMany(CountDownLatch go, UUID as, UUID conversation, String prefix) throws Exception {
        go.await();
        try {
            for (int i = 0; i < 5; i++) {
                send(as, conversation, prefix + "-" + i);
            }
            return null;
        } finally {
            SignedIn.out();
        }
    }

    // --- sending, retrying, recalling ------------------------------------------------------------------------------

    @Test
    void aRetriedClientIdReturnsTheFirstMessageAndStoresOneRow() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);

        SendMessageOutcome first = send(a, conversation, "retry-me", "hello");
        SendMessageOutcome retry = send(a, conversation, "retry-me", "hello");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.message().id()).isEqualTo(first.message().id());
        assertThat(retry.message().seq()).isEqualTo(first.message().seq());
        assertThat(count("select count(*) from chat_messages where conversation_id = :c", "c", conversation))
                .isEqualTo(1);
        assertThat(lastSeq(conversation)).isEqualTo(1);
        assertThat(events.stream(ChatMessageSent.class).count()).isEqualTo(1);
    }

    @Test
    void aClientIdReusedForAnotherConversationIsAConflict() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        UUID c = fixture.learner();
        befriend(a, b);
        befriend(a, c);
        UUID withB = conversationOf(a, b);
        UUID withC = conversationOf(a, c);
        send(a, withB, "shared-id", "hello");

        assertThatThrownBy(() -> send(a, withC, "shared-id", "hello")).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_CLIENT_ID_REUSED"));
    }

    @Test
    void anEmptyOrOverlongMessageLeavesLastSeqWhereItWas() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        send(a, conversation, "fine");

        assertThatThrownBy(() -> send(a, conversation, "   "))
                .isInstanceOf(com.englow3.shared.error.BadRequestException.class);
        assertThatThrownBy(() -> send(a, conversation, "x".repeat(4001)))
                .isInstanceOf(com.englow3.shared.error.BadRequestException.class);

        assertThat(lastSeq(conversation)).isEqualTo(1);
        assertThat(count("select count(*) from chat_messages where conversation_id = :c", "c", conversation))
                .isEqualTo(1);
        assertThat(send(a, conversation, "next").message().seq()).isEqualTo(2);
    }

    @Test
    void aPersonOutsideTheConversationCannotSendReadOrRecall() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        UUID outsider = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatMessageResult message = send(a, conversation, "private").message();

        assertThatThrownBy(() -> send(outsider, conversation, "hi")).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));
        assertThatThrownBy(() -> messageService.history(conversation, null, null, 50)).isInstanceOfSatisfying(
                NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_CONVERSATION_NOT_FOUND"));
        assertThatThrownBy(() -> conversationService.get(conversation)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> messageService.recall(message.id())).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_FOUND"));
    }

    @Test
    void onlyTheSenderRecallsOnceAndTheMessageKeepsNothingOfWhatItSaid() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatMessageResult message = send(a, conversation, "oops").message();

        SignedIn.as(jdbc, b);
        assertThatThrownBy(() -> messageService.recall(message.id())).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_SENDER"));

        SignedIn.as(jdbc, a);
        ChatMessageResult recalled = messageService.recall(message.id());
        assertThat(recalled.recalled()).isTrue();
        assertThat(recalled.body()).isNull();
        assertThatThrownBy(() -> messageService.recall(message.id())).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_ALREADY_RECALLED"));

        assertThat(jdbc.sql("select body from chat_messages where id = :id").param("id", message.id())
                .query(String.class).optional()).isEmpty();
        assertThat(events.stream(ChatMessageRecalled.class).toList()).singleElement().satisfies(event -> {
            assertThat(event.messageId()).isEqualTo(message.id());
            assertThat(event.seq()).isEqualTo(message.seq());
            assertThat(event.recipientIds()).containsExactly(b);
        });
        SignedIn.as(jdbc, b);
        assertThat(messageService.history(conversation, null, null, 50)).singleElement().satisfies(seen -> {
            assertThat(seen.recalled()).isTrue();
            assertThat(seen.body()).isNull();
            assertThat(seen.clientId()).as("not b's message").isNull();
        });
    }

    @Test
    void aSecondRecallWaitsForTheFirstAndIsThenRefused() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        UUID messageId = send(a, conversation, "oops").message().id();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        AtomicReference<Future<ChatMessageResult>> second = new AtomicReference<>();
        try {
            // A recall in flight: it holds the message's row lock until its transaction commits.
            transactions.executeWithoutResult(tx -> {
                ChatMessage message = messageRepo.findByIdForUpdate(messageId).orElseThrow();
                message.recall(a, Instant.now());
                second.set(pool.submit(() -> {
                    SignedIn.as(jdbc, a);
                    try {
                        return messageService.recall(messageId);
                    } finally {
                        SignedIn.out();
                    }
                }));
                try {
                    Thread.sleep(1_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                assertThat(second.get().isDone()).as("the second recall waits for the lock").isFalse();
            });

            assertThatThrownBy(() -> second.get().get(30, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_ALREADY_RECALLED"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoRecallsAtOnceSucceedOnceAndRemoveTheImageOnce() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatImageUploadTicket ticket = startUpload(a, conversation);
        String key = keyOf(ticket.uploadId());
        doReturn(new StoredObjectMetadata(1_000, "image/png")).when(objectStorage).metadata("chat", key);
        UUID messageId = sendImage(a, conversation, ticket.uploadId(), "look").message().id();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<ChatMessageResult>> both = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                both.add(pool.submit(() -> {
                    go.await();
                    SignedIn.as(jdbc, a);
                    try {
                        return messageService.recall(messageId);
                    } finally {
                        SignedIn.out();
                    }
                }));
            }
            go.countDown();

            int succeeded = 0;
            int refused = 0;
            for (Future<ChatMessageResult> outcome : both) {
                try {
                    outcome.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_ALREADY_RECALLED"));
                    refused++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(refused).isEqualTo(1);
            verify(objectStorage, times(1)).delete("chat", key);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void afterUnfriendingTheNextSendIsRefusedButTheHistoryStaysReadable() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        send(a, conversation, "before");
        unfriend(a, b);

        assertThatThrownBy(() -> send(a, conversation, "after")).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));
        assertThatThrownBy(() -> send(b, conversation, "after")).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));
        assertThatThrownBy(() -> open(a, b)).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));

        assertThat(lastSeq(conversation)).isEqualTo(1);
        SignedIn.as(jdbc, b);
        assertThat(messageService.history(conversation, null, null, 50)).extracting(ChatMessageResult::body)
                .containsExactly("before");
        assertThat(conversationService.get(conversation).canSend()).isFalse();
        // Messages sent to a conversation that was unfriended are refused for images too.
        assertThatThrownBy(() -> imageService.startUpload(new StartImageUploadCommand(conversation, "image/png", 10)))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_FRIENDS"));
    }

    @Test
    void historyPagesByAfterAndBeforeSeqInAscendingOrder() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        for (int i = 1; i <= 6; i++) {
            send(i % 2 == 0 ? b : a, conversation, "m" + i);
        }
        SignedIn.as(jdbc, a);

        assertThat(messageService.history(conversation, null, null, 3)).extracting(ChatMessageResult::seq)
                .containsExactly(4L, 5L, 6L);
        assertThat(messageService.history(conversation, null, 4L, 2)).extracting(ChatMessageResult::seq)
                .containsExactly(2L, 3L);
        assertThat(messageService.history(conversation, 4L, null, 50)).extracting(ChatMessageResult::seq)
                .containsExactly(5L, 6L);
        assertThat(messageService.history(conversation, 6L, null, 50)).isEmpty();
        assertThat(messageService.history(conversation, null, null, 1000)).hasSize(6);
        assertThat(messageService.history(conversation, null, null, 50)).extracting(ChatMessageResult::clientId)
                .filteredOn(clientId -> clientId != null).as("only a's own messages carry a clientId").hasSize(3);
    }

    // --- images ----------------------------------------------------------------------------------------------------

    @Test
    void anImageIsUploadedThenSentOnceAndRecallingItDeletesTheObject() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatImageUploadTicket ticket = startUpload(a, conversation);
        String key = keyOf(ticket.uploadId());
        assertThat(key).startsWith("conversations/" + conversation + "/").endsWith(".png");
        assertThat(ticket.uploadUrl()).isEqualTo("https://storage.example/put");
        assertThat(ticket.expiresInSeconds()).isEqualTo(600);
        when(objectStorage.metadata("chat", key)).thenReturn(new StoredObjectMetadata(1_000, "image/png"));

        SendMessageOutcome sent = sendImage(a, conversation, ticket.uploadId(), null);

        assertThat(sent.created()).isTrue();
        assertThat(sent.message().body()).isNull();
        assertThat(sent.message().imageUrl()).isEqualTo("https://storage.example/get/" + key);
        assertThat(count("select count(*) from chat_image_uploads where id = :id", "id", ticket.uploadId()))
                .as("sending consumes the upload row").isZero();
        assertThatThrownBy(() -> sendImage(a, conversation, ticket.uploadId(), "again")).isInstanceOfSatisfying(
                NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_UPLOAD_NOT_FOUND"));

        SignedIn.as(jdbc, b);
        assertThat(messageService.history(conversation, null, null, 50)).singleElement()
                .satisfies(seen -> assertThat(seen.imageUrl()).isEqualTo("https://storage.example/get/" + key));
        assertThat(conversationService.list(PageRequest.of(0, 10)).getContent().get(0).lastMessage().hasImage())
                .isTrue();

        SignedIn.as(jdbc, a);
        ChatMessageResult recalled = messageService.recall(sent.message().id());
        assertThat(recalled.imageUrl()).isNull();
        verify(objectStorage).delete("chat", key);
        assertThat(count("select count(*) from chat_messages where id = :id and image_object_key is null "
                + "and body is null and recalled_at is not null", "id", sent.message().id())).isEqualTo(1);
    }

    @Test
    void sendingBeforeThePutFinishedIsAConflictAndKeepsTheUploadForTheRetry() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatImageUploadTicket ticket = startUpload(a, conversation);
        String key = keyOf(ticket.uploadId());
        when(objectStorage.metadata("chat", key))
                .thenThrow(S3Exception.builder().statusCode(404).message("no such key").build());

        assertThatThrownBy(() -> sendImage(a, conversation, ticket.uploadId(), "look")).isInstanceOfSatisfying(
                ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_NOT_UPLOADED"));

        assertThat(count("select count(*) from chat_image_uploads where id = :id", "id", ticket.uploadId()))
                .isEqualTo(1);
        assertThat(lastSeq(conversation)).isZero();

        doReturn(new StoredObjectMetadata(1_000, "image/png")).when(objectStorage).metadata("chat", key);
        assertThat(sendImage(a, conversation, ticket.uploadId(), "look").created()).isTrue();
    }

    @Test
    void anotherPersonCannotUseMyUploadOrOneFromAnotherConversation() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        UUID c = fixture.learner();
        befriend(a, b);
        befriend(a, c);
        UUID withB = conversationOf(a, b);
        UUID withC = conversationOf(a, c);
        ChatImageUploadTicket ticket = startUpload(a, withB);

        assertThatThrownBy(() -> sendImage(b, withB, ticket.uploadId(), "mine now")).isInstanceOfSatisfying(
                NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_UPLOAD_NOT_FOUND"));
        assertThatThrownBy(() -> sendImage(a, withC, ticket.uploadId(), "wrong chat")).isInstanceOfSatisfying(
                NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_IMAGE_UPLOAD_NOT_FOUND"));
        verify(objectStorage, never()).metadata(anyString(), anyString());
    }

    @Test
    void theSweepRemovesOnlyUploadsOlderThanADayAndTheirObjects() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        UUID stale = insertUpload(conversation, a, "conversations/%s/stale.png".formatted(conversation), "48 hours");
        UUID fresh = insertUpload(conversation, a, "conversations/%s/fresh.png".formatted(conversation), "1 hour");

        int removed = imageService.removeAbandonedUploads();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from chat_image_uploads where id = :id", "id", stale)).isZero();
        assertThat(count("select count(*) from chat_image_uploads where id = :id", "id", fresh)).isEqualTo(1);
        verify(objectStorage).delete("chat", "conversations/%s/stale.png".formatted(conversation));
        verify(objectStorage, never()).delete("chat", "conversations/%s/fresh.png".formatted(conversation));
    }

    private UUID insertUpload(UUID conversation, UUID uploader, String key, String age) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into chat_image_uploads (id, conversation_id, uploader_id, object_key, content_type,
                                                content_length, created_at)
                values (:id, :c, :u, :key, 'image/png', 10, now() - cast(:age as interval))
                """).param("id", id).param("c", conversation).param("u", uploader).param("key", key).param("age", age)
                .update();
        return id;
    }

    // --- constraints -----------------------------------------------------------------------------------------------

    private UUID bareConversation() {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into chat_conversations (id, direct_key) values (:id, :key)").param("id", id)
                .param("key", id + ":" + UUID.randomUUID()).update();
        return id;
    }

    private void insertMessage(UUID conversation, long seq, UUID sender, String body, String imageKey,
            boolean recalled) {
        jdbc.sql("""
                insert into chat_messages (id, conversation_id, seq, sender_id, client_id, body, image_object_key,
                                           recalled_at)
                values (:id, :c, :seq, :sender, :client, :body, :image, case when :recalled then now() end)
                """).param("id", UUID.randomUUID()).param("c", conversation).param("seq", seq).param("sender", sender)
                .param("client", UUID.randomUUID().toString()).param("body", body).param("image", imageKey)
                .param("recalled", recalled).update();
    }

    @Test
    void theDatabaseRefusesAnEmptyMessageARecalledOneThatKeepsContentAndATooLongBody() {
        UUID a = fixture.learner();
        UUID conversation = bareConversation();

        assertThatThrownBy(() -> insertMessage(conversation, 1, a, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_chat_messages_content");
        assertThatThrownBy(() -> insertMessage(conversation, 1, a, "still here", null, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_chat_messages_content");
        assertThatThrownBy(() -> insertMessage(conversation, 1, a, null, "conversations/c/u.png", true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_chat_messages_content");
        assertThatThrownBy(() -> insertMessage(conversation, 1, a, "x".repeat(4001), null, false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_chat_messages_body_length");

        insertMessage(conversation, 1, a, "x".repeat(4000), null, false);
        insertMessage(conversation, 2, a, null, "conversations/c/u.png", false);
        insertMessage(conversation, 3, a, null, null, true);
    }

    @Test
    void theDatabaseKeepsSeqAndClientIdUniqueAndAConversationKeyUnique() {
        UUID a = fixture.learner();
        UUID conversation = bareConversation();
        insertMessage(conversation, 1, a, "first", null, false);

        assertThatThrownBy(() -> insertMessage(conversation, 1, a, "same seq", null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_chat_messages_seq");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into chat_messages (id, conversation_id, seq, sender_id, client_id, body)
                select :id, :c, 2, sender_id, client_id, 'same client' from chat_messages where conversation_id = :c
                """).param("id", UUID.randomUUID()).param("c", conversation).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_chat_messages_client");
        String key = jdbc.sql("select direct_key from chat_conversations where id = :c").param("c", conversation)
                .query(String.class).single();
        assertThatThrownBy(() -> jdbc.sql("insert into chat_conversations (id, direct_key) values (:id, :key)")
                .param("id", UUID.randomUUID()).param("key", key).update())
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("uq_chat_conversations_direct_key");
    }

    // --- the conversation list -------------------------------------------------------------------------------------

    @Test
    void theListShowsOnlyConversationsWithMessagesNewestFirstWithTheRightUnreadCounts() {
        UUID me = fixture.learner("Me");
        UUID older = fixture.learner("Older friend");
        UUID newer = fixture.learner("Newer friend");
        UUID silent = fixture.learner("Silent friend");
        UUID recalledFriend = fixture.learner("Recalling friend");
        for (UUID friend : List.of(older, newer, silent, recalledFriend)) {
            befriend(me, friend);
        }
        UUID withOlder = conversationOf(me, older);
        UUID withNewer = conversationOf(me, newer);
        UUID withSilent = conversationOf(me, silent);
        UUID withRecalled = conversationOf(me, recalledFriend);
        send(older, withOlder, "first hello");
        send(older, withOlder, "second hello");
        send(newer, withNewer, "a" + "b".repeat(300));
        ChatMessageResult toRecall = send(recalledFriend, withRecalled, "never mind").message();
        SignedIn.as(jdbc, recalledFriend);
        messageService.recall(toRecall.id());
        setLastMessageAt(withOlder, "3 hours");
        setLastMessageAt(withRecalled, "2 hours");
        setLastMessageAt(withNewer, "1 hour");
        SignedIn.as(jdbc, me);
        conversationService.markRead(withOlder, 1);

        List<ConversationSummaryResult> list = conversationService.list(PageRequest.of(0, 10)).getContent();

        assertThat(list).extracting(ConversationSummaryResult::id).containsExactly(withNewer, withRecalled, withOlder);
        assertThat(list).extracting(ConversationSummaryResult::id).doesNotContain(withSilent);
        ConversationSummaryResult newest = list.get(0);
        assertThat(newest.otherUser().displayName()).isEqualTo("Newer friend");
        assertThat(newest.unreadCount()).isEqualTo(1);
        assertThat(newest.lastMessage().preview()).hasSize(120).startsWith("ab");
        assertThat(newest.canSend()).as("not worked out for a list").isNull();
        ConversationSummaryResult recalledOne = list.get(1);
        assertThat(recalledOne.lastMessage().recalled()).isTrue();
        assertThat(recalledOne.lastMessage().preview()).isNull();
        ConversationSummaryResult partlyRead = list.get(2);
        assertThat(partlyRead.lastSeq()).isEqualTo(2);
        assertThat(partlyRead.lastReadSeq()).isEqualTo(1);
        assertThat(partlyRead.unreadCount()).isEqualTo(1);
        assertThat(partlyRead.lastMessage().preview()).isEqualTo("second hello");

        assertThat(conversationService.unreadConversationCount()).isEqualTo(3);
        assertThat(conversationService.list(PageRequest.of(0, 2)).getContent())
                .extracting(ConversationSummaryResult::id).containsExactly(withNewer, withRecalled);
        assertThat(conversationService.list(PageRequest.of(1, 2)).getContent())
                .extracting(ConversationSummaryResult::id).containsExactly(withOlder);
        assertThat(conversationService.list(PageRequest.of(0, 2)).getTotalElements()).isEqualTo(3);

        conversationService.markRead(withNewer, 1);
        conversationService.markRead(withRecalled, 1);
        conversationService.markRead(withOlder, 2);
        assertThat(conversationService.unreadConversationCount()).isZero();
        SignedIn.as(jdbc, silent);
        assertThat(conversationService.list(PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    private void setLastMessageAt(UUID conversation, String ago) {
        jdbc.sql("update chat_conversations set last_message_at = now() - cast(:ago as interval) where id = :c")
                .param("ago", ago).param("c", conversation).update();
    }

    // --- cleanup of empty conversations ----------------------------------------------------------------------------

    @Test
    void deletingEmptyConversationsIsConditionalAndSaysWhatItRemoved() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID inUse = conversationOf(a, b);
        UUID empty = bareConversation();

        List<UUID> first = transactions.execute(tx -> conversationRepo.deleteIfEmpty(List.of(inUse, empty)));
        List<UUID> second = transactions.execute(tx -> conversationRepo.deleteIfEmpty(List.of(inUse, empty)));

        assertThat(first).containsExactly(empty);
        assertThat(second).as("the other sweep finds it already gone").isEmpty();
        assertThat(count("select count(*) from chat_conversations where id = :c", "c", inUse)).isEqualTo(1);
        assertThat(count("select count(*) from chat_conversations where id = :c", "c", empty)).isZero();
    }

    @Test
    void twoSweepsAtOnceNeitherFailsAndEachImageIsRemovedOnce() throws Exception {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            UUID conversation = bareConversation();
            String key = "conversations/%s/%s.png".formatted(conversation, UUID.randomUUID());
            insertMessage(conversation, 1, null, null, key, false);
            keys.add(key);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> sweeps = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                sweeps.add(pool.submit(() -> {
                    go.await();
                    return conversationService.removeEmptyConversations();
                }));
            }
            go.countDown();

            int removed = 0;
            for (Future<Integer> sweep : sweeps) {
                removed += sweep.get(60, TimeUnit.SECONDS);
            }
            assertThat(removed).as("between them, each conversation once").isGreaterThanOrEqualTo(25);
            for (String key : keys) {
                verify(objectStorage, times(1)).delete("chat", key);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- erasure ---------------------------------------------------------------------------------------------------

    @Test
    void erasingOneAccountKeepsTheMessagesAsFromNobodyAndErasingBothRemovesTheConversationAndItsImages() {
        UUID a = fixture.learner("Leaver");
        UUID b = fixture.learner("Stayer");
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        send(a, conversation, "from a");
        ChatImageUploadTicket ticket = startUpload(a, conversation);
        String key = keyOf(ticket.uploadId());
        when(objectStorage.metadata("chat", key)).thenReturn(new StoredObjectMetadata(1_000, "image/png"));
        sendImage(a, conversation, ticket.uploadId(), "a picture");
        send(b, conversation, "from b");

        jdbc.sql("delete from users where id = :a").param("a", a).update();

        assertThat(count("select count(*) from chat_participants where conversation_id = :c and user_id = :a", "c",
                conversation, "a", a)).isZero();
        assertThat(count("select count(*) from chat_participants where conversation_id = :c", "c", conversation))
                .isEqualTo(1);
        assertThat(count("select count(*) from chat_messages where conversation_id = :c and sender_id is null", "c",
                conversation)).isEqualTo(2);
        assertThat(count("select count(*) from chat_messages where conversation_id = :c", "c", conversation))
                .isEqualTo(3);
        SignedIn.as(jdbc, b);
        ConversationSummaryResult asSeenByB = conversationService.list(PageRequest.of(0, 10)).getContent().stream()
                .filter(summary -> summary.id().equals(conversation)).findFirst().orElseThrow();
        assertThat(asSeenByB.otherUser()).isNull();
        assertThat(conversationService.get(conversation).otherUser()).isNull();
        assertThat(conversationService.get(conversation).canSend()).isFalse();
        assertThat(messageService.history(conversation, null, null, 50)).extracting(ChatMessageResult::senderId)
                .containsExactly(null, null, b);
        assertThatThrownBy(() -> send(b, conversation, "anyone there?")).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_PARTNER_GONE"));
        // The sweep also removes other tests' empty conversations, so only this one is asserted on: b is still in it.
        conversationService.removeEmptyConversations();
        assertThat(count("select count(*) from chat_conversations where id = :c", "c", conversation)).isEqualTo(1);
        // A message sent by someone who has gone cannot be recalled by anyone.
        ChatMessageResult ghostMessage = messageService.history(conversation, null, null, 50).get(0);
        assertThatThrownBy(() -> messageService.recall(ghostMessage.id())).isInstanceOfSatisfying(
                ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_MESSAGE_NOT_SENDER"));

        jdbc.sql("delete from users where id = :b").param("b", b).update();
        int removed = conversationService.removeEmptyConversations();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from chat_conversations where id = :c", "c", conversation)).isZero();
        assertThat(count("select count(*) from chat_messages where conversation_id = :c", "c", conversation)).isZero();
        verify(objectStorage).delete("chat", key);
    }

    @Test
    void anAccountWithAPendingUploadCanStillBeErased() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatImageUploadTicket ticket = startUpload(a, conversation);

        jdbc.sql("delete from users where id = :a").param("a", a).update();

        assertThat(count("select count(*) from chat_image_uploads where id = :id", "id", ticket.uploadId())).isZero();
    }
}
