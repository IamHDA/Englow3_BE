package com.englow3.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;

import com.englow3.chat.dto.command.SendMessageCommand;
import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatMessageService;
import com.englow3.chat.service.ChatReadAdvanced;
import com.englow3.chat.service.ChatSocketRegistry;
import com.englow3.chat.service.ChatSocketTicketService;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The whole path, over a real socket on a real port: a ticket asked for over the service, redeemed by the handshake, a
 * message committed by the REST-side service and pushed after commit to the other person's open socket. The server runs
 * in this JVM, so the test signs in on its own thread (to ask for tickets and to send) while the socket handshake runs
 * on the server's threads with no token at all - which is exactly the browser case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatSocketEndToEndIntegrationTest extends PostgresIntegrationTest {

    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    int port;
    @Value("${app.cors.allowed-origins}")
    List<String> allowedOrigins;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ChatSocketTicketService ticketService;
    @Autowired
    ChatMessageService messageService;
    @Autowired
    ChatConversationService conversationService;
    @Autowired
    ChatSocketRegistry registry;
    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    TransactionTemplate transactions;

    @MockitoBean
    ObjectStorageClient objectStorage;

    private final ObjectMapper json = new ObjectMapper();
    private final List<Client> clients = new ArrayList<>();
    private LearnerFixture fixture;

    /** One open socket, collecting what the server pushes down it. */
    private static final class Client extends TextWebSocketHandler {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final UUID user;
        String ticket;
        WebSocketSession session;
        volatile CloseStatus closed;

        Client(UUID user) {
            this.user = user;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed = status;
        }

        CloseStatus awaitClosed() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (closed == null && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            return closed;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
    }

    @AfterEach
    void closeSockets() throws Exception {
        for (Client client : clients) {
            if (client.session != null && client.session.isOpen()) {
                client.session.close();
            }
        }
        clients.clear();
        SignedIn.out();
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private String newTicketFor(UUID user) {
        SignedIn.as(jdbc, user);
        try {
            return ticketService.issue().ticket();
        } finally {
            SignedIn.out();
        }
    }

    private WebSocketSession open(Client client, String ticket, String origin) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        URI uri = URI.create("ws://localhost:%d/ws/chat?ticket=%s".formatted(port, ticket));
        return new StandardWebSocketClient().execute(client, headers, uri).get(10, TimeUnit.SECONDS);
    }

    /** A connected socket for the user, ready to be pushed to. */
    private Client connect(UUID user) throws Exception {
        Client client = new Client(user);
        client.ticket = newTicketFor(user);
        client.session = open(client, client.ticket, allowedOrigins.get(0));
        clients.add(client);
        awaitRegistered(client);
        return client;
    }

    /**
     * The client has its 101 a moment before the server has registered the socket. A throwaway frame to the person is
     * the honest way to know the server is ready: it arrives only once the socket is in the registry.
     */
    private void awaitRegistered(Client client) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            registry.sendTo(client.user, new ChatSocketFrame.Typing(UUID.randomUUID(), UUID.randomUUID(), false));
            if (client.frames.poll(100, TimeUnit.MILLISECONDS) != null) {
                // Let any second probe that was already on its way arrive, then forget them all.
                Thread.sleep(150);
                client.frames.clear();
                return;
            }
        }
        throw new AssertionError("The server never registered the socket");
    }

    private JsonNode next(Client client) throws Exception {
        String frame = client.frames.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(frame).as("a frame within %d seconds", WAIT_SECONDS).isNotNull();
        return json.readTree(frame);
    }

    private void befriend(UUID a, UUID b) {
        jdbc.sql("""
                insert into friendships (id, requester_id, addressee_id, status, accepted_at)
                values (:id, :a, :b, 'ACCEPTED', now())
                """).param("id", UUID.randomUUID()).param("a", a).param("b", b).update();
    }

    private UUID conversationOf(UUID a, UUID b) {
        SignedIn.as(jdbc, a);
        return conversationService.open(b).conversation().id();
    }

    private ChatMessageResult send(UUID as, UUID conversation, String body) {
        SignedIn.as(jdbc, as);
        return messageService.send(new SendMessageCommand(conversation, UUID.randomUUID().toString(), body, null))
                .message();
    }

    // --- tests -----------------------------------------------------------------------------------------------------

    @Test
    void aMessageSentByOnePersonReachesTheOthersOpenSocketAndTheSendersOwnOtherSocket() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        Client forB = connect(b);
        Client forA = connect(a);

        ChatMessageResult sent = send(a, conversation, "hello b");

        JsonNode toB = next(forB);
        assertThat(toB.get("type").asText()).isEqualTo("message");
        JsonNode message = toB.get("message");
        assertThat(message.get("seq").asLong()).isEqualTo(sent.seq()).isEqualTo(1);
        assertThat(message.get("body").asText()).isEqualTo("hello b");
        assertThat(message.get("id").asText()).isEqualTo(sent.id().toString());
        assertThat(message.get("conversationId").asText()).isEqualTo(conversation.toString());
        assertThat(message.get("senderId").asText()).isEqualTo(a.toString());
        assertThat(message.get("recalled").asBoolean()).isFalse();
        assertThat(message.get("imageUrl").isNull()).isTrue();
        assertThat(message.get("clientId").isNull()).as("not b's own message").isTrue();

        JsonNode toA = next(forA);
        assertThat(toA.get("type").asText()).isEqualTo("message");
        assertThat(toA.get("message").get("seq").asLong()).isEqualTo(1);
        assertThat(toA.get("message").get("clientId").asText()).as("a's own message").isEqualTo(sent.clientId());
    }

    @Test
    void theSameTicketCannotOpenASecondSocket() throws Exception {
        UUID b = fixture.learner();
        Client first = connect(b);

        assertThatThrownBy(() -> open(new Client(b), first.ticket, allowedOrigins.get(0)))
                .isInstanceOf(ExecutionException.class).hasMessageContaining("401");
    }

    @Test
    void aMissingOrUnknownTicketIsRefused() {
        UUID b = fixture.learner();

        assertThatThrownBy(() -> open(new Client(b), "never-issued", allowedOrigins.get(0)))
                .isInstanceOf(ExecutionException.class).hasMessageContaining("401");
        assertThatThrownBy(() -> open(new Client(b), "", allowedOrigins.get(0))).isInstanceOf(ExecutionException.class)
                .hasMessageContaining("401");
    }

    @Test
    void aHandshakeFromAnOriginThatIsNotAllowedIsRefused() {
        UUID b = fixture.learner();
        String ticket = newTicketFor(b);

        assertThatThrownBy(() -> open(new Client(b), ticket, "https://evil.example"))
                .isInstanceOf(ExecutionException.class).hasMessageContaining("403");
    }

    @Test
    void aTypingSignalIsRelayedToTheOtherPersonOnlyAndNothingIsStored() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        Client forA = connect(a);
        Client forB = connect(b);

        forA.session
                .sendMessage(new TextMessage("{\"conversationId\":\"%s\",\"active\":true}".formatted(conversation)));

        JsonNode typing = next(forB);
        assertThat(typing.get("type").asText()).isEqualTo("typing");
        assertThat(typing.get("conversationId").asText()).isEqualTo(conversation.toString());
        assertThat(typing.get("userId").asText()).isEqualTo(a.toString());
        assertThat(typing.get("active").asBoolean()).isTrue();
        assertThat(forA.frames.poll(500, TimeUnit.MILLISECONDS)).as("not echoed to the one typing").isNull();
        assertThat(jdbc.sql("select count(*) from chat_messages where conversation_id = :c").param("c", conversation)
                .query(Long.class).single()).isZero();
    }

    @Test
    void junkFramesAndATypingSignalForAConversationNotTheirsAreIgnoredAndTheSocketStaysOpen() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        UUID stranger = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        Client forB = connect(b);
        Client forStranger = connect(stranger);

        forStranger.session.sendMessage(new TextMessage("not json at all"));
        forStranger.session.sendMessage(new TextMessage("{\"nothing\":true}"));
        forStranger.session
                .sendMessage(new TextMessage("{\"conversationId\":\"%s\",\"active\":true}".formatted(conversation)));

        assertThat(forB.frames.poll(800, TimeUnit.MILLISECONDS)).as("a stranger cannot make b see typing").isNull();
        assertThat(forStranger.session.isOpen()).isTrue();
        // And the socket still works afterwards.
        send(a, conversation, "still here");
        assertThat(next(forB).get("type").asText()).isEqualTo("message");
    }

    @Test
    void aReadMarkAndARecallAreAnnouncedToTheOtherPersonAndToTheActorsOwnSockets() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        ChatMessageResult sent = send(a, conversation, "take this back");
        Client forA = connect(a);
        Client forB = connect(b);

        SignedIn.as(jdbc, b);
        conversationService.markRead(conversation, 1);

        JsonNode readSeenByA = next(forA);
        assertThat(readSeenByA.get("type").asText()).isEqualTo("read");
        assertThat(readSeenByA.get("conversationId").asText()).isEqualTo(conversation.toString());
        assertThat(readSeenByA.get("userId").asText()).isEqualTo(b.toString());
        assertThat(readSeenByA.get("upToSeq").asLong()).isEqualTo(1);
        assertThat(next(forB).get("type").asText()).as("b's other tabs follow the unread badge").isEqualTo("read");

        SignedIn.as(jdbc, a);
        messageService.recall(sent.id());

        for (Client client : List.of(forB, forA)) {
            JsonNode recalled = next(client);
            assertThat(recalled.get("type").asText()).isEqualTo("recalled");
            assertThat(recalled.get("conversationId").asText()).isEqualTo(conversation.toString());
            assertThat(recalled.get("messageId").asText()).isEqualTo(sent.id().toString());
            assertThat(recalled.get("seq").asLong()).isEqualTo(1);
        }
    }

    @Test
    void aPushWaitsForTheCommitOfTheTransactionThatPublishedAndNeverHappensOnARollback() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        Client forB = connect(b);

        transactions.executeWithoutResult(tx -> {
            publisher.publishEvent(new ChatReadAdvanced(conversation, a, 5, Set.of(b)));
            try {
                assertThat(forB.frames.poll(500, TimeUnit.MILLISECONDS)).as("nothing before the commit").isNull();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        JsonNode afterCommit = next(forB);
        assertThat(afterCommit.get("type").asText()).isEqualTo("read");
        assertThat(afterCommit.get("upToSeq").asLong()).isEqualTo(5);

        transactions.executeWithoutResult(tx -> {
            publisher.publishEvent(new ChatReadAdvanced(conversation, a, 6, Set.of(b)));
            tx.setRollbackOnly();
        });
        assertThat(forB.frames.poll(800, TimeUnit.MILLISECONDS)).as("a rolled-back change is never pushed").isNull();
    }

    @Test
    void theSixthSocketOfOnePersonClosesTheOldestWithAPolicyViolation() throws Exception {
        UUID b = fixture.learner();
        Client oldest = connect(b);
        List<Client> others = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            others.add(connect(b));
        }
        assertThat(oldest.closed).as("five sockets are allowed").isNull();

        Client sixth = connect(b);

        assertThat(oldest.awaitClosed()).isEqualTo(CloseStatus.POLICY_VIOLATION);
        assertThat(sixth.session.isOpen()).isTrue();
        assertThat(others).allSatisfy(other -> assertThat(other.closed).isNull());
    }

    @Test
    void aFrameLargerThanTheTypingSignalNeedsClosesTheSocketAsTooBig() throws Exception {
        UUID b = fixture.learner();
        Client client = connect(b);

        client.session.sendMessage(new TextMessage("x".repeat(5_000)));

        // The server adds its own reason text; the code is what a client acts on.
        assertThat(client.awaitClosed().getCode()).isEqualTo(CloseStatus.TOO_BIG_TO_PROCESS.getCode());
    }

    @Test
    void sendingWhileTheOtherPersonHasNoSocketStillSucceedsAndLeavesTheMessageToBeCaughtUpOverRest() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);

        ChatMessageResult sent = send(a, conversation, "while you were away");

        assertThat(sent.seq()).isEqualTo(1);
        SignedIn.as(jdbc, b);
        assertThat(messageService.history(conversation, 0L, null, 50)).extracting(ChatMessageResult::body)
                .containsExactly("while you were away");
    }

    @Test
    void aSocketOfSomeoneWhoIsNotAParticipantNeverHearsTheConversation() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        UUID stranger = fixture.learner();
        befriend(a, b);
        UUID conversation = conversationOf(a, b);
        Client forStranger = connect(stranger);
        Client forB = connect(b);

        send(a, conversation, "private");

        assertThat(next(forB).get("type").asText()).isEqualTo("message");
        assertThat(forStranger.frames.poll(800, TimeUnit.MILLISECONDS)).isNull();
    }
}
