package com.englow3.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.chat.dto.result.ChatSocketTicket;
import com.englow3.chat.helper.SocketTickets;
import com.englow3.chat.service.ChatSocketTicketService;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * What a mock cannot show: that one conditional update lets a ticket be redeemed once even when two handshakes present
 * it at the same moment, that only the hash reaches the table, and that tickets go with the account.
 */
class ChatSocketTicketIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    ChatSocketTicketService ticketService;

    private LearnerFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    private ChatSocketTicket issueFor(UUID user) {
        SignedIn.as(jdbc, user);
        return ticketService.issue();
    }

    private long rows(String where, Object... namedParams) {
        JdbcClient.StatementSpec spec = jdbc.sql("select count(*) from chat_socket_tickets where " + where);
        for (int i = 0; i < namedParams.length; i += 2) {
            spec = spec.param((String) namedParams[i], namedParams[i + 1]);
        }
        return spec.query(Long.class).single();
    }

    @Test
    void aTicketRedeemsToItsOwnerOnceAndNeverAgain() {
        UUID user = fixture.learner();
        ChatSocketTicket ticket = issueFor(user);

        assertThat(ticketService.redeem(ticket.ticket())).contains(user);
        assertThat(ticketService.redeem(ticket.ticket())).isEmpty();
        assertThat(rows("token_hash = :h and used_at is not null", "h", SocketTickets.hash(ticket.ticket())))
                .isEqualTo(1);
    }

    @Test
    void theTableHoldsTheHashAndNeverTheTicket() {
        UUID user = fixture.learner();

        ChatSocketTicket ticket = issueFor(user);

        assertThat(ticket.ticket()).hasSize(43);
        assertThat(ticket.expiresInSeconds()).isEqualTo(30);
        assertThat(rows("token_hash = :h and user_id = :u", "h", SocketTickets.hash(ticket.ticket()), "u", user))
                .isEqualTo(1);
        assertThat(rows("token_hash = :t", "t", ticket.ticket())).isZero();
        assertThat(
                rows("user_id = :u and expires_at > now() and expires_at <= now() + interval '31 seconds'", "u", user))
                        .isEqualTo(1);
    }

    @Test
    void twoHandshakesPresentingOneTicketAtOnceCannotBothWin() throws Exception {
        UUID user = fixture.learner();
        ChatSocketTicket ticket = issueFor(user);
        SignedIn.out();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Optional<UUID>>> attempts = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                attempts.add(pool.submit(() -> {
                    go.await();
                    return ticketService.redeem(ticket.ticket());
                }));
            }
            go.countDown();

            int winners = 0;
            for (Future<Optional<UUID>> attempt : attempts) {
                if (attempt.get(30, TimeUnit.SECONDS).isPresent()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anExpiredTicketIsNotRedeemed() {
        UUID user = fixture.learner();
        ChatSocketTicket ticket = issueFor(user);
        jdbc.sql("update chat_socket_tickets set expires_at = now() - interval '1 second' where token_hash = :h")
                .param("h", SocketTickets.hash(ticket.ticket())).update();

        assertThat(ticketService.redeem(ticket.ticket())).isEmpty();
        assertThat(rows("token_hash = :h and used_at is null", "h", SocketTickets.hash(ticket.ticket())))
                .as("a refused redemption does not mark it used").isEqualTo(1);
    }

    @Test
    void aTicketThatWasNeverIssuedIsNotRedeemed() {
        assertThat(ticketService.redeem(SocketTickets.newTicket(new java.security.SecureRandom()))).isEmpty();
        assertThat(ticketService.redeem("not-a-ticket")).isEmpty();
        assertThat(ticketService.redeem(null)).isEmpty();
    }

    @Test
    void erasingTheAccountRemovesItsTickets() {
        UUID user = fixture.learner();
        ChatSocketTicket ticket = issueFor(user);

        jdbc.sql("delete from users where id = :u").param("u", user).update();

        assertThat(rows("user_id = :u", "u", user)).isZero();
        assertThat(ticketService.redeem(ticket.ticket())).isEmpty();
    }

    @Test
    void staffAndAdminGetNoTicket() {
        UUID staff = fixture.staff("Staff member");
        SignedIn.as(jdbc, staff);

        assertThatThrownBy(() -> ticketService.issue()).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_LEARNERS_ONLY"));
        assertThat(rows("user_id = :u", "u", staff)).isZero();
    }

    @Test
    void theSweepRemovesTicketsThatExpiredMoreThanAnHourAgoAndNoOthers() {
        UUID user = fixture.learner();
        String old = insertTicket(user, "2 hours");
        String recent = insertTicket(user, "5 minutes");
        ChatSocketTicket live = issueFor(user);

        int removed = ticketService.removeExpired();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(rows("token_hash = :h", "h", old)).isZero();
        assertThat(rows("token_hash = :h", "h", recent)).isEqualTo(1);
        assertThat(rows("token_hash = :h", "h", SocketTickets.hash(live.ticket()))).isEqualTo(1);
    }

    private String insertTicket(UUID user, String expiredAgo) {
        String hash = SocketTickets.hash(UUID.randomUUID().toString());
        jdbc.sql("""
                insert into chat_socket_tickets (token_hash, user_id, expires_at)
                values (:h, :u, now() - cast(:ago as interval))
                """).param("h", hash).param("u", user).param("ago", expiredAgo).update();
        return hash;
    }
}
