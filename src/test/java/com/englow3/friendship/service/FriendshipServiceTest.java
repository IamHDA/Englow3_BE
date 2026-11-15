package com.englow3.friendship.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.englow3.friendship.dto.command.SendFriendRequestCommand;
import com.englow3.friendship.dto.result.FriendRequestOutcome;
import com.englow3.friendship.dto.result.FriendRequestResult;
import com.englow3.friendship.dto.result.FriendResult;
import com.englow3.friendship.dto.result.LearnerSearchResult;
import com.englow3.friendship.dto.result.RelationshipResult;
import com.englow3.friendship.entity.Friendship;
import com.englow3.friendship.entity.FriendshipStatus;
import com.englow3.friendship.entity.RelationshipStatus;
import com.englow3.friendship.repository.FriendshipRepository;
import com.englow3.friendship.repository.FriendshipRepository.PairIds;
import com.englow3.friendship.repository.UserBlockRepository;
import com.englow3.friendship.service.impl.FriendshipServiceImpl;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: which entity method is called with what, in which order, and which repository answers decide the
 * branch. The rules inside {@link Friendship} are covered by its own test, so the entity is a mock here.
 */
class FriendshipServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final FriendshipRepository friendshipRepo = mock(FriendshipRepository.class);
    private final UserBlockRepository userBlockRepo = mock(UserBlockRepository.class);
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final FriendshipService service = new FriendshipServiceImpl(userDirectory, publicProfileReader,
            friendshipRepo, userBlockRepo, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void signIn() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(publicProfileReader.isLearner(me)).thenReturn(true);
        when(publicProfileReader.isLearner(other)).thenReturn(true);
        when(friendshipRepo.save(any(Friendship.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static PublicProfile profile(UUID id, String name) {
        return new PublicProfile(id, name, null);
    }

    private static PairIds pair(UUID requesterId, UUID addresseeId) {
        PairIds pair = mock(PairIds.class);
        when(pair.getRequesterId()).thenReturn(requesterId);
        when(pair.getAddresseeId()).thenReturn(addresseeId);
        return pair;
    }

    /** A request whose other party, as seen from {@code me}, is {@code them}. */
    private Friendship requestWith(UUID them, FriendshipStatus status) {
        Friendship friendship = mock(Friendship.class);
        when(friendship.getId()).thenReturn(UUID.randomUUID());
        when(friendship.otherParty(me)).thenReturn(them);
        when(friendship.getStatus()).thenReturn(status);
        return friendship;
    }

    @Nested
    class SendingARequest {

        @Test
        void takesThePairLockBeforeReadingAndSavesANewRequest() {
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.empty());
            when(publicProfileReader.profiles(Set.of(other))).thenReturn(Map.of(other, profile(other, "Bao")));

            FriendRequestOutcome outcome = service.sendRequest(new SendFriendRequestCommand(other));

            InOrder order = inOrder(friendshipRepo);
            order.verify(friendshipRepo).lockPair(Friendship.pairKey(me, other));
            order.verify(friendshipRepo).findPair(me, other);
            ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepo).save(saved.capture());
            assertThat(saved.getValue().getRequesterId()).isEqualTo(me);
            assertThat(saved.getValue().getAddresseeId()).isEqualTo(other);
            assertThat(saved.getValue().getRequestedAt()).isEqualTo(now);
            assertThat(outcome.created()).isTrue();
            assertThat(outcome.request().userId()).isEqualTo(other);
            assertThat(outcome.request().displayName()).isEqualTo("Bao");
        }

        @Test
        void isRefusedWhenEitherSideHasBlockedTheOtherWithoutSaying() {
            when(userBlockRepo.existsEitherWay(me, other)).thenReturn(true);

            assertThatThrownBy(() -> service.sendRequest(new SendFriendRequestCommand(other))).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_ALLOWED"));
            verify(friendshipRepo, never()).findPair(any(), any());
            verify(friendshipRepo, never()).save(any());
        }

        @Test
        void staffCannotSendOneAndTakeNoLock() {
            when(publicProfileReader.isLearner(me)).thenReturn(false);

            assertThatThrownBy(() -> service.sendRequest(new SendFriendRequestCommand(other))).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_LEARNERS_ONLY"));
            verify(friendshipRepo, never()).lockPair(anyString());
            verifyNoInteractions(userBlockRepo);
        }

        @Test
        void aTargetThatIsNotALearnerIsNotFound() {
            when(publicProfileReader.isLearner(other)).thenReturn(false);

            assertThatThrownBy(() -> service.sendRequest(new SendFriendRequestCommand(other))).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("LEARNER_NOT_FOUND"));
            verifyNoInteractions(friendshipRepo, userBlockRepo);
        }

        @Test
        void answersTheOtherLearnersRequestInsteadOfCreatingASecondRow() {
            Friendship theirs = requestWith(other, FriendshipStatus.PENDING);
            when(theirs.isRequestedBy(me)).thenReturn(false);
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.of(theirs));

            FriendRequestOutcome outcome = service.sendRequest(new SendFriendRequestCommand(other));

            verify(theirs).accept(me, now);
            verify(friendshipRepo, never()).save(any());
            assertThat(outcome.created()).isFalse();
        }

        @Test
        void resendingYourOwnPendingRequestHandsBackTheSameOne() {
            Friendship mine = requestWith(other, FriendshipStatus.PENDING);
            when(mine.isRequestedBy(me)).thenReturn(true);
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.of(mine));

            FriendRequestOutcome outcome = service.sendRequest(new SendFriendRequestCommand(other));

            verify(mine, never()).accept(any(), any());
            verify(friendshipRepo, never()).save(any());
            assertThat(outcome.created()).isFalse();
            assertThat(outcome.request().requestId()).isEqualTo(mine.getId());
        }

        @Test
        void askingSomeoneAlreadyAFriendIsAConflict() {
            Friendship friendship = requestWith(other, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.of(friendship));

            assertThatThrownBy(() -> service.sendRequest(new SendFriendRequestCommand(other))).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("ALREADY_FRIENDS"));
            verify(friendshipRepo, never()).save(any());
        }
    }

    @Nested
    class AnsweringARequest {

        private final UUID requestId = UUID.randomUUID();

        @Test
        void acceptLocksThePairBeforeLoadingTheRequestThenLetsTheEntityAccept() {
            Friendship request = requestWith(other, FriendshipStatus.ACCEPTED);
            PairIds theirPair = pair(other, me);
            when(friendshipRepo.findPairIds(requestId)).thenReturn(Optional.of(theirPair));
            when(friendshipRepo.findById(requestId)).thenReturn(Optional.of(request));

            FriendRequestOutcome outcome = service.accept(requestId);

            InOrder order = inOrder(friendshipRepo, request);
            order.verify(friendshipRepo).findPairIds(requestId);
            order.verify(friendshipRepo).lockPair(Friendship.pairKey(me, other));
            order.verify(friendshipRepo).findById(requestId);
            order.verify(request).accept(me, now);
            assertThat(outcome.created()).isFalse();
            assertThat(outcome.request().userId()).isEqualTo(other);
        }

        @Test
        void aRequestBetweenOtherPeopleIsNotFoundAndNothingIsLockedOrLoaded() {
            PairIds strangers = pair(UUID.randomUUID(), UUID.randomUUID());
            when(friendshipRepo.findPairIds(requestId)).thenReturn(Optional.of(strangers));

            assertThatThrownBy(() -> service.accept(requestId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_FOUND"));
            verify(friendshipRepo, never()).lockPair(anyString());
            verify(friendshipRepo, never()).findById(any());
        }

        @Test
        void aRequestThatDoesNotExistIsNotFound() {
            when(friendshipRepo.findPairIds(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.accept(requestId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_FOUND"));
            verify(friendshipRepo, never()).lockPair(anyString());
        }

        @Test
        void aRequestWithdrawnWhileWaitingForTheLockIsNotFound() {
            PairIds theirPair = pair(other, me);
            when(friendshipRepo.findPairIds(requestId)).thenReturn(Optional.of(theirPair));
            when(friendshipRepo.findById(requestId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.accept(requestId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_FOUND"));
        }

        @Test
        void staffCannotAccept() {
            when(publicProfileReader.isLearner(me)).thenReturn(false);

            assertThatThrownBy(() -> service.accept(requestId)).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_LEARNERS_ONLY"));
            verifyNoInteractions(friendshipRepo);
        }

        @Test
        void withdrawLocksThenAsksTheEntityBeforeDeleting() {
            Friendship request = requestWith(other, FriendshipStatus.PENDING);
            PairIds myPair = pair(me, other);
            when(friendshipRepo.findPairIds(requestId)).thenReturn(Optional.of(myPair));
            when(friendshipRepo.findById(requestId)).thenReturn(Optional.of(request));

            service.withdraw(requestId);

            InOrder order = inOrder(friendshipRepo, request);
            order.verify(friendshipRepo).lockPair(Friendship.pairKey(me, other));
            order.verify(friendshipRepo).findById(requestId);
            order.verify(request).ensureWithdrawableBy(me);
            order.verify(friendshipRepo).delete(request);
        }

        @Test
        void aWithdrawnRequestBetweenOtherPeopleIsNotFoundAndNotDeleted() {
            PairIds strangers = pair(UUID.randomUUID(), UUID.randomUUID());
            when(friendshipRepo.findPairIds(requestId)).thenReturn(Optional.of(strangers));

            assertThatThrownBy(() -> service.withdraw(requestId)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_FOUND"));
            verify(friendshipRepo, never()).delete(any());
        }
    }

    @Nested
    class Unfriending {

        @Test
        void locksThePairThenAsksTheEntityBeforeDeleting() {
            Friendship friendship = requestWith(other, FriendshipStatus.ACCEPTED);
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.of(friendship));

            service.unfriend(other);

            InOrder order = inOrder(friendshipRepo, friendship);
            order.verify(friendshipRepo).lockPair(Friendship.pairKey(me, other));
            order.verify(friendshipRepo).findPair(me, other);
            order.verify(friendship).ensureUnfriendableBy(me);
            order.verify(friendshipRepo).delete(friendship);
        }

        @Test
        void aLearnerYouAreNotConnectedToIsNotFound() {
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unfriend(other)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_NOT_FOUND"));
            verify(friendshipRepo, never()).delete(any());
        }
    }

    @Nested
    class Lists {

        @Test
        void friendsAskTheProfileReaderOnceForTheWholePage() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            Instant firstSince = Instant.parse("2026-11-01T00:00:00Z");
            Friendship one = requestWith(first, FriendshipStatus.ACCEPTED);
            Friendship two = requestWith(second, FriendshipStatus.ACCEPTED);
            when(one.getAcceptedAt()).thenReturn(firstSince);
            when(friendshipRepo.findFriendships(eq(me), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(one, two)));
            when(publicProfileReader.profiles(Set.of(first, second)))
                    .thenReturn(Map.of(first, profile(first, "An"), second, profile(second, "Binh")));

            Page<FriendResult> page = service.friends(PageRequest.of(0, 20));

            verify(publicProfileReader, times(1)).profiles(anyCollection());
            assertThat(page.getContent()).extracting(FriendResult::userId, FriendResult::displayName).containsExactly(
                    org.assertj.core.groups.Tuple.tuple(first, "An"),
                    org.assertj.core.groups.Tuple.tuple(second, "Binh"));
            assertThat(page.getContent().get(0).friendsSince()).isEqualTo(firstSince);
        }

        @Test
        void thePageOrderIsTheServicesNotTheCallers() {
            when(friendshipRepo.findFriendships(eq(me), any(Pageable.class))).thenReturn(Page.empty());

            service.friends(PageRequest.of(2, 10, Sort.by("displayName")));

            ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
            verify(friendshipRepo).findFriendships(eq(me), asked.capture());
            assertThat(asked.getValue().getPageNumber()).isEqualTo(2);
            assertThat(asked.getValue().getPageSize()).isEqualTo(10);
            assertThat(asked.getValue().getSort()).containsExactly(Sort.Order.desc("acceptedAt"), Sort.Order.asc("id"));
        }

        @Test
        void incomingRequestsAreThosePendingForTheCaller() {
            UUID requester = UUID.randomUUID();
            Friendship request = requestWith(requester, FriendshipStatus.PENDING);
            when(friendshipRepo.findByAddresseeIdAndStatus(eq(me), eq(FriendshipStatus.PENDING), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(request)));
            when(publicProfileReader.profiles(Set.of(requester)))
                    .thenReturn(Map.of(requester, profile(requester, "Chi")));

            Page<FriendRequestResult> page = service.incomingRequests(PageRequest.of(0, 20));

            assertThat(page.getContent()).singleElement().satisfies(result -> {
                assertThat(result.userId()).isEqualTo(requester);
                assertThat(result.displayName()).isEqualTo("Chi");
            });
            ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
            verify(friendshipRepo).findByAddresseeIdAndStatus(eq(me), eq(FriendshipStatus.PENDING), asked.capture());
            assertThat(asked.getValue().getSort()).containsExactly(Sort.Order.desc("requestedAt"),
                    Sort.Order.asc("id"));
        }

        @Test
        void outgoingRequestsAreThoseTheCallerSentAndNobodyAnswered() {
            when(friendshipRepo.findByRequesterIdAndStatus(eq(me), eq(FriendshipStatus.PENDING), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.outgoingRequests(PageRequest.of(0, 20));

            verify(friendshipRepo).findByRequesterIdAndStatus(eq(me), eq(FriendshipStatus.PENDING),
                    any(Pageable.class));
        }

        @Test
        void countsOnlyPendingRequestsAddressedToTheCaller() {
            when(friendshipRepo.countByAddresseeIdAndStatus(me, FriendshipStatus.PENDING)).thenReturn(3L);

            assertThat(service.incomingRequestCount()).isEqualTo(3L);
        }
    }

    @Nested
    class SearchingLearners {

        @Test
        void aTermUnderTwoCharactersIsRefusedBeforeAnythingIsAsked() {
            assertThatThrownBy(() -> service.searchLearners(" a ", PageRequest.of(0, 20))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("SEARCH_TERM_TOO_SHORT"));
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void leavesOutTheCallerAndEveryoneBlockedEitherWay() {
            UUID iBlocked = UUID.randomUUID();
            UUID blockedMe = UUID.randomUUID();
            when(userBlockRepo.blockedBy(me)).thenReturn(Set.of(iBlocked));
            when(userBlockRepo.blockersOf(me)).thenReturn(Set.of(blockedMe));
            when(publicProfileReader.searchLearners(anyString(), anyCollection(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            service.searchLearners("  an ", PageRequest.of(0, 20));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<UUID>> excluded = ArgumentCaptor.forClass(Collection.class);
            verify(publicProfileReader).searchLearners(eq("an"), excluded.capture(), any(Pageable.class));
            assertThat(excluded.getValue()).containsExactlyInAnyOrder(me, iBlocked, blockedMe);
        }

        @Test
        void anEmptyPageDoesNotLookUpRelationships() {
            when(publicProfileReader.searchLearners(anyString(), anyCollection(), any(Pageable.class)))
                    .thenReturn(Page.empty());

            Page<LearnerSearchResult> page = service.searchLearners("an", PageRequest.of(0, 20));

            assertThat(page).isEmpty();
            verify(friendshipRepo, never()).findPairsWith(any(), anyCollection());
        }

        @Test
        void saysHowTheCallerStandsWithEachLearnerFound() {
            UUID friend = UUID.randomUUID();
            UUID invitedMe = UUID.randomUUID();
            UUID iInvited = UUID.randomUUID();
            UUID stranger = UUID.randomUUID();
            Friendship withFriend = requestWith(friend, FriendshipStatus.ACCEPTED);
            Friendship received = requestWith(invitedMe, FriendshipStatus.PENDING);
            when(received.isRequestedBy(me)).thenReturn(false);
            Friendship sent = requestWith(iInvited, FriendshipStatus.PENDING);
            when(sent.isRequestedBy(me)).thenReturn(true);
            when(friendshipRepo.findPairsWith(eq(me), anyCollection())).thenReturn(List.of(withFriend, received, sent));
            when(publicProfileReader.searchLearners(anyString(), anyCollection(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(profile(friend, "A"), profile(invitedMe, "B"),
                            profile(iInvited, "C"), profile(stranger, "D"))));

            Page<LearnerSearchResult> page = service.searchLearners("an", PageRequest.of(0, 20));

            assertThat(page.getContent()).extracting(LearnerSearchResult::relationship).containsExactly(
                    RelationshipStatus.FRIENDS, RelationshipStatus.REQUEST_RECEIVED, RelationshipStatus.REQUEST_SENT,
                    RelationshipStatus.NONE);
            assertThat(page.getContent().get(1).requestId()).isEqualTo(received.getId());
            assertThat(page.getContent()).filteredOn(result -> result != page.getContent().get(1))
                    .allSatisfy(result -> assertThat(result.requestId()).isNull());
        }

        @Test
        void staffCannotSearch() {
            when(publicProfileReader.isLearner(me)).thenReturn(false);

            assertThatThrownBy(() -> service.searchLearners("an", PageRequest.of(0, 20))).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_LEARNERS_ONLY"));
            verify(publicProfileReader, never()).searchLearners(anyString(), anyCollection(), any(Pageable.class));
        }
    }

    @Nested
    class RelationshipWithOneLearner {

        @Test
        void yourselfIsSelfAndNothingIsQueried() {
            RelationshipResult result = service.relationshipWith(me);

            assertThat(result.relationship()).isEqualTo(RelationshipStatus.SELF);
            assertThat(result.requestId()).isNull();
            verifyNoInteractions(friendshipRepo, userBlockRepo, publicProfileReader);
        }

        @Test
        void aLearnerYouBlockedIsBlockedByMe() {
            when(userBlockRepo.existsByIdBlockerIdAndIdBlockedId(me, other)).thenReturn(true);

            assertThat(service.relationshipWith(other).relationship()).isEqualTo(RelationshipStatus.BLOCKED_BY_ME);
            verify(friendshipRepo, never()).findPair(any(), any());
        }

        @Test
        void aLearnerWhoBlockedYouIsOnlyUnavailable() {
            when(userBlockRepo.existsByIdBlockerIdAndIdBlockedId(other, me)).thenReturn(true);

            RelationshipResult result = service.relationshipWith(other);

            assertThat(result.relationship()).isEqualTo(RelationshipStatus.UNAVAILABLE);
            assertThat(result.requestId()).isNull();
        }

        @Test
        void staffOrAnUnknownIdIsNotApplicableAndNeverAnError() {
            when(publicProfileReader.isLearner(other)).thenReturn(false);

            RelationshipResult result = service.relationshipWith(other);

            assertThat(result.relationship()).isEqualTo(RelationshipStatus.NOT_APPLICABLE);
            assertThat(result.requestId()).isNull();
            verifyNoInteractions(friendshipRepo, userBlockRepo);
        }

        @Test
        void aCallerWhoIsStaffGetsNotApplicableForEveryone() {
            when(publicProfileReader.isLearner(me)).thenReturn(false);

            RelationshipResult result = service.relationshipWith(other);

            assertThat(result.relationship()).isEqualTo(RelationshipStatus.NOT_APPLICABLE);
            verifyNoInteractions(friendshipRepo, userBlockRepo);
        }

        @Test
        void aRequestWaitingForYouCarriesItsId() {
            Friendship received = requestWith(other, FriendshipStatus.PENDING);
            when(received.isRequestedBy(me)).thenReturn(false);
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.of(received));

            RelationshipResult result = service.relationshipWith(other);

            assertThat(result.relationship()).isEqualTo(RelationshipStatus.REQUEST_RECEIVED);
            assertThat(result.requestId()).isEqualTo(received.getId());
        }

        @Test
        void noConnectionIsNone() {
            when(friendshipRepo.findPair(me, other)).thenReturn(Optional.empty());

            assertThat(service.relationshipWith(other).relationship()).isEqualTo(RelationshipStatus.NONE);
        }
    }

    @Nested
    class FriendCountOfALearner {

        @Test
        void countsAnotherLearnersFriendsWhenNobodyBlockedAnybody() {
            when(userBlockRepo.existsEitherWay(me, other)).thenReturn(false);
            when(friendshipRepo.countFriendsOf(other)).thenReturn(7L);

            assertThat(service.friendCountOf(other)).isEqualTo(7L);
        }

        @Test
        void aBlockEitherWayHidesTheLearnerAsIfTheyWereMissing() {
            when(userBlockRepo.existsEitherWay(me, other)).thenReturn(true);

            assertThatThrownBy(() -> service.friendCountOf(other)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("LEARNER_NOT_FOUND"));
            verify(friendshipRepo, never()).countFriendsOf(any());
        }

        @Test
        void yourOwnCountNeverChecksForBlocks() {
            when(friendshipRepo.countFriendsOf(me)).thenReturn(3L);

            assertThat(service.friendCountOf(me)).isEqualTo(3L);
            verifyNoInteractions(userBlockRepo);
        }

        @Test
        void aCallerWhoIsStaffMayStillSeeTheNumber() {
            when(publicProfileReader.isLearner(me)).thenReturn(false);
            when(friendshipRepo.countFriendsOf(other)).thenReturn(2L);

            assertThat(service.friendCountOf(other)).isEqualTo(2L);
        }
    }
}
