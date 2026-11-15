package com.englow3.friendship.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;

class FriendshipTest {

    private static final Instant REQUESTED = Instant.parse("2026-11-15T08:00:00Z");
    private static final Instant ACCEPTED = Instant.parse("2026-11-15T09:30:00Z");

    private final UUID requester = UUID.randomUUID();
    private final UUID addressee = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private Friendship pending() {
        return Friendship.request(requester, addressee, REQUESTED);
    }

    private Friendship accepted() {
        Friendship friendship = pending();
        friendship.accept(addressee, ACCEPTED);
        return friendship;
    }

    @Nested
    class Requesting {

        @Test
        void startsPendingWithTheTimeItWasAskedAndNoAcceptance() {
            Friendship friendship = pending();

            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(friendship.getRequesterId()).isEqualTo(requester);
            assertThat(friendship.getAddresseeId()).isEqualTo(addressee);
            assertThat(friendship.getRequestedAt()).isEqualTo(REQUESTED);
            assertThat(friendship.getAcceptedAt()).isNull();
            assertThat(friendship.getId()).isNotNull();
        }

        @Test
        void refusesToBefriendYourself() {
            assertThatThrownBy(() -> Friendship.request(requester, requester, REQUESTED)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_SELF"));
        }
    }

    @Nested
    class Accepting {

        @Test
        void theAddresseeAcceptsAndTheTimeIsRecorded() {
            Friendship friendship = pending();

            friendship.accept(addressee, ACCEPTED);

            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(friendship.getAcceptedAt()).isEqualTo(ACCEPTED);
        }

        @Test
        void theRequesterCannotAcceptTheirOwnRequest() {
            Friendship friendship = pending();

            assertThatThrownBy(() -> friendship.accept(requester, ACCEPTED)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_ADDRESSEE"));
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        void aRequestCannotBeAcceptedTwice() {
            Friendship friendship = accepted();

            assertThatThrownBy(() -> friendship.accept(addressee, ACCEPTED.plusSeconds(60))).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_PENDING"));
            assertThat(friendship.getAcceptedAt()).isEqualTo(ACCEPTED);
        }
    }

    @Nested
    class Withdrawing {

        @Test
        void eitherSideMayWithdrawAPendingRequest() {
            Friendship friendship = pending();

            assertThatCode(() -> friendship.ensureWithdrawableBy(requester)).doesNotThrowAnyException();
            assertThatCode(() -> friendship.ensureWithdrawableBy(addressee)).doesNotThrowAnyException();
        }

        @Test
        void someoneOutsideThePairMayNotWithdraw() {
            Friendship friendship = pending();

            assertThatThrownBy(() -> friendship.ensureWithdrawableBy(stranger)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_NOT_INVOLVED"));
        }

        @Test
        void anAcceptedFriendshipIsEndedByUnfriendingNotWithdrawing() {
            Friendship friendship = accepted();

            assertThatThrownBy(() -> friendship.ensureWithdrawableBy(requester)).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("FRIEND_REQUEST_NOT_PENDING"));
        }
    }

    @Nested
    class Unfriending {

        @Test
        void eitherSideMayEndAnAcceptedFriendship() {
            Friendship friendship = accepted();

            assertThatCode(() -> friendship.ensureUnfriendableBy(requester)).doesNotThrowAnyException();
            assertThatCode(() -> friendship.ensureUnfriendableBy(addressee)).doesNotThrowAnyException();
        }

        @Test
        void aPendingRequestIsNotAFriendshipYet() {
            Friendship friendship = pending();

            assertThatThrownBy(() -> friendship.ensureUnfriendableBy(requester)).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_NOT_ACCEPTED"));
        }

        @Test
        void someoneOutsideThePairMayNotUnfriend() {
            Friendship friendship = accepted();

            assertThatThrownBy(() -> friendship.ensureUnfriendableBy(stranger)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("FRIENDSHIP_NOT_INVOLVED"));
        }
    }

    @Nested
    class ThePair {

        @Test
        void knowsWhoIsInvolvedAndWhoAsked() {
            Friendship friendship = pending();

            assertThat(friendship.involves(requester)).isTrue();
            assertThat(friendship.involves(addressee)).isTrue();
            assertThat(friendship.involves(stranger)).isFalse();
            assertThat(friendship.isRequestedBy(requester)).isTrue();
            assertThat(friendship.isRequestedBy(addressee)).isFalse();
        }

        @Test
        void theOtherPartyIsWhoeverTheAskerIsNot() {
            Friendship friendship = pending();

            assertThat(friendship.otherParty(requester)).isEqualTo(addressee);
            assertThat(friendship.otherParty(addressee)).isEqualTo(requester);
        }

        @Test
        void askingForTheOtherPartyOfSomeoneOutsideThePairIsAProgrammingError() {
            Friendship friendship = pending();

            assertThatThrownBy(() -> friendship.otherParty(stranger)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void theLockKeyIsTheSameWhicheverIdComesFirst() {
            assertThat(Friendship.pairKey(requester, addressee)).isEqualTo(Friendship.pairKey(addressee, requester));
        }

        @Test
        void differentPairsGetDifferentLockKeys() {
            assertThat(Friendship.pairKey(requester, addressee)).isNotEqualTo(Friendship.pairKey(requester, stranger));
        }
    }
}
