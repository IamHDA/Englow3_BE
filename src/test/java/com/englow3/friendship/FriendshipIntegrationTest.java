package com.englow3.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.friendship.api.FriendshipReader;
import com.englow3.friendship.dto.command.SendFriendRequestCommand;
import com.englow3.friendship.dto.result.BlockedUserResult;
import com.englow3.friendship.dto.result.FriendRequestOutcome;
import com.englow3.friendship.dto.result.FriendRequestResult;
import com.englow3.friendship.dto.result.FriendResult;
import com.englow3.friendship.dto.result.LearnerSearchResult;
import com.englow3.friendship.entity.FriendshipStatus;
import com.englow3.friendship.entity.RelationshipStatus;
import com.englow3.friendship.service.BlockService;
import com.englow3.friendship.service.FriendshipService;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * What a mock cannot show: the unique index that makes a pair one row, the cascade from {@code users}, the hand-written
 * LIKE and sort clauses against a real database, and the pair lock under two real transactions. Each test creates its
 * own learners, so tests sharing the container never see each other's rows.
 */
class FriendshipIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    FriendshipService friendshipService;
    @Autowired
    BlockService blockService;
    @Autowired
    FriendshipReader friendshipReader;

    private LearnerFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new LearnerFixture(jdbc);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    private FriendRequestOutcome sendAs(UUID from, UUID to) {
        SignedIn.as(jdbc, from);
        return friendshipService.sendRequest(new SendFriendRequestCommand(to));
    }

    private void insertFriendship(UUID requester, UUID addressee, FriendshipStatus status) {
        jdbc.sql("""
                insert into friendships (id, requester_id, addressee_id, status, accepted_at)
                values (:id, :requester, :addressee, :status, case when :status = 'ACCEPTED' then now() end)
                """).param("id", UUID.randomUUID()).param("requester", requester).param("addressee", addressee)
                .param("status", status.name()).update();
    }

    private long rowsBetween(UUID a, UUID b) {
        return jdbc.sql("""
                select count(*) from friendships
                where (requester_id = :a and addressee_id = :b) or (requester_id = :b and addressee_id = :a)
                """).param("a", a).param("b", b).query(Long.class).single();
    }

    private String statusBetween(UUID a, UUID b) {
        return jdbc.sql("""
                select status from friendships
                where (requester_id = :a and addressee_id = :b) or (requester_id = :b and addressee_id = :a)
                """).param("a", a).param("b", b).query(String.class).single();
    }

    private static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aPairCanHaveOnlyOneRowWhicheverSideAsked() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        insertFriendship(a, b, FriendshipStatus.PENDING);

        assertThatThrownBy(() -> insertFriendship(b, a, FriendshipStatus.PENDING))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_friendships_pair");
    }

    @Test
    void theDatabaseRefusesASelfFriendshipAndAnAcceptedRowWithoutATime() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();

        assertThatThrownBy(() -> insertFriendship(a, a, FriendshipStatus.PENDING))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_friendships_not_self");
        assertThatThrownBy(() -> jdbc.sql("""
                insert into friendships (id, requester_id, addressee_id, status)
                values (:id, :a, :b, 'ACCEPTED')
                """).param("id", UUID.randomUUID()).param("a", a).param("b", b).update())
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_friendships_accepted_at");
    }

    @Test
    void crossedRequestsEndAsOneAcceptedFriendship() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();

        FriendRequestOutcome first = sendAs(a, b);
        FriendRequestOutcome second = sendAs(b, a);

        assertThat(first.created()).isTrue();
        assertThat(first.request().status()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(second.created()).isFalse();
        assertThat(second.request().status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(second.request().requestId()).isEqualTo(first.request().requestId());
        assertThat(rowsBetween(a, b)).isEqualTo(1);
        assertThat(statusBetween(a, b)).isEqualTo("ACCEPTED");
    }

    @Test
    void twoCrossedRequestsAtTheSameMomentStillMakeOneRow() throws Exception {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<FriendRequestOutcome> fromA = pool.submit(() -> {
                go.await();
                return sendAs(a, b);
            });
            Future<FriendRequestOutcome> fromB = pool.submit(() -> {
                go.await();
                return sendAs(b, a);
            });
            go.countDown();

            FriendRequestOutcome outcomeA = fromA.get(30, TimeUnit.SECONDS);
            FriendRequestOutcome outcomeB = fromB.get(30, TimeUnit.SECONDS);

            // Whoever got the lock second answers the first one's request: exactly one of them created the row.
            assertThat(List.of(outcomeA.created(), outcomeB.created())).containsExactlyInAnyOrder(true, false);
            assertThat(rowsBetween(a, b)).isEqualTo(1);
            assertThat(statusBetween(a, b)).isEqualTo("ACCEPTED");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theInvitedLearnerSeesTheRequestAcceptsItAndBothSeeAFriend() {
        UUID a = fixture.learner("Asker " + tag());
        UUID b = fixture.learner("Invited " + tag());
        FriendRequestOutcome sent = sendAs(a, b);

        SignedIn.as(jdbc, b);
        assertThat(friendshipService.incomingRequestCount()).isEqualTo(1);
        List<FriendRequestResult> incoming = friendshipService.incomingRequests(PageRequest.of(0, 20)).getContent();
        assertThat(incoming).singleElement().satisfies(request -> {
            assertThat(request.requestId()).isEqualTo(sent.request().requestId());
            assertThat(request.userId()).isEqualTo(a);
            assertThat(request.displayName()).startsWith("Asker ");
        });

        FriendRequestOutcome accepted = friendshipService.accept(sent.request().requestId());
        assertThat(accepted.request().status()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(accepted.request().acceptedAt()).isNotNull();
        assertThat(friendshipService.incomingRequestCount()).isZero();
        assertThat(friendshipService.friends(PageRequest.of(0, 20)).getContent()).extracting(FriendResult::userId)
                .containsExactly(a);

        SignedIn.as(jdbc, a);
        assertThat(friendshipService.friends(PageRequest.of(0, 20)).getContent()).extracting(FriendResult::userId)
                .containsExactly(b);
        assertThat(friendshipService.outgoingRequests(PageRequest.of(0, 20))).isEmpty();
        assertThat(friendshipReader.areFriends(a, b)).isTrue();
        assertThat(friendshipReader.areFriends(b, a)).isTrue();
    }

    @Test
    void decliningCancellingAndUnfriendingEachDeleteTheRow() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();

        UUID declined = sendAs(a, b).request().requestId();
        SignedIn.as(jdbc, b);
        friendshipService.withdraw(declined);
        assertThat(rowsBetween(a, b)).isZero();

        UUID cancelled = sendAs(a, b).request().requestId();
        friendshipService.withdraw(cancelled);
        assertThat(rowsBetween(a, b)).isZero();

        UUID accepted = sendAs(a, b).request().requestId();
        SignedIn.as(jdbc, b);
        friendshipService.accept(accepted);
        SignedIn.as(jdbc, a);
        friendshipService.unfriend(b);
        assertThat(rowsBetween(a, b)).isZero();
    }

    @Test
    void blockingAFriendDeletesTheFriendshipAndStopsANewRequestEitherWay() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        insertFriendship(a, b, FriendshipStatus.ACCEPTED);

        SignedIn.as(jdbc, b);
        blockService.block(a);

        assertThat(rowsBetween(a, b)).isZero();
        assertThat(jdbc.sql("select count(*) from user_blocks where blocker_id = :b and blocked_id = :a").param("a", a)
                .param("b", b).query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> sendAs(a, b)).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_ALLOWED"));
        assertThatThrownBy(() -> sendAs(b, a)).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_ALLOWED"));

        SignedIn.as(jdbc, b);
        blockService.unblock(a);
        assertThat(rowsBetween(a, b)).as("unblocking does not bring the friendship back").isZero();
        blockService.unblock(a);
    }

    @Test
    void blockingAPendingRequestDeletesItToo() {
        UUID a = fixture.learner();
        UUID b = fixture.learner();
        sendAs(a, b);

        SignedIn.as(jdbc, b);
        blockService.block(a);
        blockService.block(a);

        assertThat(rowsBetween(a, b)).isZero();
    }

    @Test
    void theReaderAnswersForBothDirectionsOfABlock() {
        UUID a = fixture.learner();
        UUID iBlocked = fixture.learner();
        UUID blockedMe = fixture.learner();
        UUID unrelated = fixture.learner();
        SignedIn.as(jdbc, a);
        blockService.block(iBlocked);
        SignedIn.as(jdbc, blockedMe);
        blockService.block(a);

        assertThat(friendshipReader.blockedEitherWay(a)).containsExactlyInAnyOrder(iBlocked, blockedMe);
        assertThat(friendshipReader.isBlockedEitherWay(a, iBlocked)).isTrue();
        assertThat(friendshipReader.isBlockedEitherWay(iBlocked, a)).isTrue();
        assertThat(friendshipReader.isBlockedEitherWay(blockedMe, a)).isTrue();
        assertThat(friendshipReader.isBlockedEitherWay(a, unrelated)).isFalse();
        assertThat(friendshipReader.blockedEitherWay(unrelated)).isEmpty();
    }

    @Test
    void theBlockedListShowsTheNewestBlockFirst() {
        UUID a = fixture.learner();
        UUID older = fixture.learner("Older " + tag());
        UUID newer = fixture.learner("Newer " + tag());
        jdbc.sql(
                "insert into user_blocks (blocker_id, blocked_id, created_at) values (:a, :b, now() - interval '2 hours')")
                .param("a", a).param("b", older).update();
        jdbc.sql(
                "insert into user_blocks (blocker_id, blocked_id, created_at) values (:a, :b, now() - interval '1 hour')")
                .param("a", a).param("b", newer).update();

        SignedIn.as(jdbc, a);
        List<BlockedUserResult> blocked = blockService.blocked(PageRequest.of(0, 10)).getContent();

        assertThat(blocked).extracting(BlockedUserResult::userId).containsExactly(newer, older);
        assertThat(blocked.get(0).displayName()).startsWith("Newer ");
    }

    @Test
    void searchFindsOnlyLearnersTheCallerMayInvite() {
        String tag = tag();
        UUID me = fixture.learner("Me-" + tag);
        UUID candidate = fixture.learner("Candidate-" + tag);
        UUID iBlocked = fixture.learner("Blocked-by-me-" + tag);
        UUID blockedMe = fixture.learner("Blocked-me-" + tag);
        fixture.staff("Staff-" + tag);
        SignedIn.as(jdbc, me);
        blockService.block(iBlocked);
        SignedIn.as(jdbc, blockedMe);
        blockService.block(me);

        SignedIn.as(jdbc, me);
        List<LearnerSearchResult> found = friendshipService.searchLearners("-" + tag, PageRequest.of(0, 20))
                .getContent();

        assertThat(found).extracting(LearnerSearchResult::userId).containsExactly(candidate);
        assertThat(found.get(0).relationship()).isEqualTo(RelationshipStatus.NONE);
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsPlainCharacters() {
        String tag = tag();
        UUID me = fixture.learner("Me " + tag);
        UUID percent = fixture.learner("Sale 50% off " + tag);
        fixture.learner("Sale 500 off " + tag);
        UUID underscore = fixture.learner("snake_case " + tag);
        fixture.learner("snakeXcase " + tag);

        SignedIn.as(jdbc, me);

        assertThat(friendshipService.searchLearners("50% off " + tag, PageRequest.of(0, 20)).getContent())
                .extracting(LearnerSearchResult::userId).containsExactly(percent);
        assertThat(friendshipService.searchLearners("snake_case " + tag, PageRequest.of(0, 20)).getContent())
                .extracting(LearnerSearchResult::userId).containsExactly(underscore);
    }

    @Test
    void searchSaysHowTheCallerStandsWithEachLearner() {
        String tag = tag();
        UUID me = fixture.learner("Me-" + tag);
        UUID friend = fixture.learner("Friend-" + tag);
        UUID invitedMe = fixture.learner("Invitedme-" + tag);
        UUID iInvited = fixture.learner("Iinvited-" + tag);
        UUID stranger = fixture.learner("Stranger-" + tag);
        insertFriendship(me, friend, FriendshipStatus.ACCEPTED);
        insertFriendship(invitedMe, me, FriendshipStatus.PENDING);
        insertFriendship(me, iInvited, FriendshipStatus.PENDING);

        SignedIn.as(jdbc, me);
        Map<UUID, LearnerSearchResult> byUser = friendshipService.searchLearners("-" + tag, PageRequest.of(0, 20))
                .getContent().stream().collect(Collectors.toMap(LearnerSearchResult::userId, Function.identity()));

        assertThat(byUser).containsOnlyKeys(friend, invitedMe, iInvited, stranger);
        assertThat(byUser.get(friend).relationship()).isEqualTo(RelationshipStatus.FRIENDS);
        assertThat(byUser.get(invitedMe).relationship()).isEqualTo(RelationshipStatus.REQUEST_RECEIVED);
        assertThat(byUser.get(invitedMe).requestId()).isNotNull();
        assertThat(byUser.get(iInvited).relationship()).isEqualTo(RelationshipStatus.REQUEST_SENT);
        assertThat(byUser.get(iInvited).requestId()).isNull();
        assertThat(byUser.get(stranger).relationship()).isEqualTo(RelationshipStatus.NONE);
        assertThat(friendshipService.relationshipWith(invitedMe).requestId())
                .isEqualTo(byUser.get(invitedMe).requestId());
    }

    @Test
    void relationshipAnswersWithAStateForEveryoneAndNeverAnError() {
        UUID me = fixture.learner();
        UUID staffAuthor = fixture.staff("Staff author " + tag());
        UUID blockedMe = fixture.learner();
        UUID iBlocked = fixture.learner();
        UUID stranger = fixture.learner();
        SignedIn.as(jdbc, blockedMe);
        blockService.block(me);
        SignedIn.as(jdbc, me);
        blockService.block(iBlocked);

        assertThat(friendshipService.relationshipWith(me).relationship()).isEqualTo(RelationshipStatus.SELF);
        assertThat(friendshipService.relationshipWith(staffAuthor).relationship())
                .isEqualTo(RelationshipStatus.NOT_APPLICABLE);
        assertThat(friendshipService.relationshipWith(UUID.randomUUID()).relationship())
                .isEqualTo(RelationshipStatus.NOT_APPLICABLE);
        assertThat(friendshipService.relationshipWith(blockedMe).relationship())
                .isEqualTo(RelationshipStatus.UNAVAILABLE);
        assertThat(friendshipService.relationshipWith(iBlocked).relationship())
                .isEqualTo(RelationshipStatus.BLOCKED_BY_ME);
        assertThat(friendshipService.relationshipWith(stranger).relationship()).isEqualTo(RelationshipStatus.NONE);

        SignedIn.as(jdbc, staffAuthor);
        assertThat(friendshipService.relationshipWith(stranger).relationship())
                .isEqualTo(RelationshipStatus.NOT_APPLICABLE);
    }

    @Test
    void friendCountCountsBothSidesIgnoresPendingAndHidesBlockedLearners() {
        UUID me = fixture.learner();
        UUID asked = fixture.learner();
        UUID askedMe = fixture.learner();
        UUID pending = fixture.learner();
        UUID blocker = fixture.learner();
        UUID target = fixture.learner();
        insertFriendship(target, asked, FriendshipStatus.ACCEPTED);
        insertFriendship(askedMe, target, FriendshipStatus.ACCEPTED);
        insertFriendship(target, pending, FriendshipStatus.PENDING);
        insertFriendship(blocker, target, FriendshipStatus.ACCEPTED);

        SignedIn.as(jdbc, me);
        assertThat(friendshipService.friendCountOf(target)).as("both directions, never the pending one").isEqualTo(3);
        assertThat(friendshipService.friendCountOf(UUID.randomUUID())).isZero();

        SignedIn.as(jdbc, target);
        assertThat(friendshipService.friendCountOf(target)).isEqualTo(3);
        blockService.block(me);

        SignedIn.as(jdbc, me);
        assertThatThrownBy(() -> friendshipService.friendCountOf(target)).isInstanceOfSatisfying(
                com.englow3.shared.error.NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("LEARNER_NOT_FOUND"));
        SignedIn.as(jdbc, target);
        assertThatThrownBy(() -> friendshipService.friendCountOf(me)).isInstanceOfSatisfying(
                com.englow3.shared.error.NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("LEARNER_NOT_FOUND"));
        assertThat(friendshipService.friendCountOf(target)).as("one's own count ignores blocks").isEqualTo(3);
    }

    @Test
    void staffCannotSearchOrBefriend() {
        UUID staff = fixture.staff("Staff " + tag());
        UUID learner = fixture.learner();
        SignedIn.as(jdbc, staff);

        assertThatThrownBy(() -> friendshipService.searchLearners("ab", PageRequest.of(0, 20))).isInstanceOfSatisfying(
                ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_LEARNERS_ONLY"));
        assertThatThrownBy(() -> sendAs(staff, learner)).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_LEARNERS_ONLY"));
    }

    @Test
    void erasingAnAccountRemovesItsFriendshipsAndBlocks() {
        UUID a = fixture.learner();
        UUID friend = fixture.learner();
        UUID blocked = fixture.learner();
        UUID blocker = fixture.learner();
        insertFriendship(a, friend, FriendshipStatus.ACCEPTED);
        jdbc.sql("insert into user_blocks (blocker_id, blocked_id) values (:a, :blocked)").param("a", a)
                .param("blocked", blocked).update();
        jdbc.sql("insert into user_blocks (blocker_id, blocked_id) values (:blocker, :a)").param("blocker", blocker)
                .param("a", a).update();

        jdbc.sql("delete from users where id = :a").param("a", a).update();

        assertThat(rowsBetween(a, friend)).isZero();
        assertThat(jdbc.sql("select count(*) from user_blocks where blocker_id = :a or blocked_id = :a").param("a", a)
                .query(Long.class).single()).isZero();
        assertThat(friendshipReader.blockedEitherWay(blocked)).isEmpty();
        assertThat(Set.copyOf(friendshipReader.blockedEitherWay(blocker))).isEmpty();
    }
}
