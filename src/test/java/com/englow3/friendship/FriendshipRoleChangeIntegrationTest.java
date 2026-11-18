package com.englow3.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.friendship.api.FriendshipReader;
import com.englow3.friendship.dto.command.SendFriendRequestCommand;
import com.englow3.friendship.entity.FriendshipStatus;
import com.englow3.friendship.entity.RelationshipStatus;
import com.englow3.friendship.service.FriendshipService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.englow3.user.entity.Role;
import com.englow3.user.service.AdminUserService;

/** The role update and social write must serialize against the same real user rows. */
class FriendshipRoleChangeIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    JdbcClient jdbc;
    @Autowired
    FriendshipService friends;
    @Autowired
    FriendshipReader reader;
    @Autowired
    AdminUserService adminUsers;
    @Autowired
    PlatformTransactionManager transactions;

    private UUID sender;
    private UUID recipient;
    private UUID admin;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        sender = fixture.learner("Role-change sender");
        recipient = fixture.learner("Role-change recipient");
        admin = fixture.staff("Role-change administrator");
        jdbc.sql("update users set role = 'ADMIN' where id = :id").param("id", admin).update();
        SignedIn.as(jdbc, sender);
        requestId = friends.sendRequest(new SendFriendRequestCommand(recipient)).request().requestId();
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    private void changeSenderRole(Role expected, Role replacement) {
        SignedIn.as(jdbc, admin, "ROLE_ADMIN");
        adminUsers.changeRole(sender, expected, replacement, "Regression: social role transition");
    }

    @Test
    void pendingRequestsAreInactiveUntilBothPeopleAreLearnersAgain() {
        changeSenderRole(Role.LEARNER, Role.STAFF);
        SignedIn.as(jdbc, recipient);
        assertThat(friends.incomingRequestCount()).isZero();
        assertThat(friends.incomingRequests(PageRequest.of(0, 20))).isEmpty();
        assertThat(friends.relationshipWith(sender).relationship()).isEqualTo(RelationshipStatus.NOT_APPLICABLE);
        assertThatThrownBy(() -> friends.accept(requestId)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("LEARNER_NOT_FOUND"));
        SignedIn.as(jdbc, sender);
        assertThat(friends.outgoingRequests(PageRequest.of(0, 20))).isEmpty();

        changeSenderRole(Role.STAFF, Role.LEARNER);
        SignedIn.as(jdbc, recipient);
        assertThat(friends.incomingRequestCount()).isEqualTo(1);
        assertThat(friends.accept(requestId).request().status()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void acceptedConnectionsAreSuspendedAndCanStillBeRemoved() {
        SignedIn.as(jdbc, recipient);
        friends.accept(requestId);
        changeSenderRole(Role.LEARNER, Role.STAFF);
        assertThat(reader.areFriends(sender, recipient)).isFalse();
        SignedIn.as(jdbc, recipient);
        assertThat(friends.friends(PageRequest.of(0, 20))).isEmpty();
        assertThat(friends.friendCountOf(sender)).isZero();
        assertThat(friends.friendCountOf(recipient)).isZero();
        SignedIn.as(jdbc, sender);
        assertThat(friends.friends(PageRequest.of(0, 20))).isEmpty();

        changeSenderRole(Role.STAFF, Role.LEARNER);
        assertThat(reader.areFriends(sender, recipient)).isTrue();
        SignedIn.as(jdbc, recipient);
        assertThat(friends.friendCountOf(sender)).isEqualTo(1);

        changeSenderRole(Role.LEARNER, Role.STAFF);
        SignedIn.as(jdbc, sender);
        friends.unfriend(recipient);
        assertThat(jdbc.sql("select count(*) from friendships where id = :id").param("id", requestId).query(Long.class)
                .single()).isZero();
    }

    @Test
    void aPromotedRequesterCanStillWithdrawTheirPendingRequest() {
        changeSenderRole(Role.LEARNER, Role.STAFF);
        SignedIn.as(jdbc, sender);
        friends.withdraw(requestId);
        assertThat(jdbc.sql("select count(*) from friendships where id = :id").param("id", requestId).query(Long.class)
                .single()).isZero();
    }

    @Test
    void acceptanceWaitsForAnInFlightPromotionAndThenRefusesIt() throws Exception {
        CountDownLatch roleWritten = new CountDownLatch(1);
        CountDownLatch commitRole = new CountDownLatch(1);
        CountDownLatch accepting = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var promotion = pool.submit(() -> {
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        changeSenderRole(Role.LEARNER, Role.STAFF);
                        roleWritten.countDown();
                        try {
                            if (!commitRole.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Role-change test did not release its transaction");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    });
                } finally {
                    SignedIn.out();
                }
            });
            try {
                assertThat(roleWritten.await(10, TimeUnit.SECONDS)).isTrue();
                var acceptance = pool.submit(() -> {
                    SignedIn.as(jdbc, recipient);
                    accepting.countDown();
                    try {
                        friends.accept(requestId);
                        return "ACCEPTED";
                    } catch (NotFoundException e) {
                        return e.getCode();
                    } finally {
                        SignedIn.out();
                    }
                });
                assertThat(accepting.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> acceptance.get(300, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                commitRole.countDown();
                promotion.get(10, TimeUnit.SECONDS);
                assertThat(acceptance.get(10, TimeUnit.SECONDS)).isEqualTo("LEARNER_NOT_FOUND");
                assertThat(jdbc.sql("select status from friendships where id = :id").param("id", requestId)
                        .query(String.class).single()).isEqualTo("PENDING");
            } finally {
                commitRole.countDown();
            }
        }
    }
}
