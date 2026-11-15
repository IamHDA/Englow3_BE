package com.englow3.friendship.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A friend request, and the friendship it becomes. One row per pair of learners whichever side asked: a request is
 * {@code PENDING} until the person invited accepts it. Declining, cancelling and unfriending delete the row, so there
 * is nothing here for a withdrawn request to be.
 * <p>
 * The rules guarded here are the ones about a single row: nobody befriends themselves, only the addressee accepts, only
 * a pending request can be accepted or withdrawn, only an accepted friendship can be ended. What spans rows - a block,
 * a request that crosses another - is the service's.
 */
@Entity
@Table(name = "friendships")
@Getter
public class Friendship extends BasePersistedEntity {

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "addressee_id", nullable = false, updatable = false)
    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected Friendship() {
    }

    public static Friendship request(UUID requesterId, UUID addresseeId, Instant now) {
        if (requesterId.equals(addresseeId)) {
            throw new BadRequestException("FRIENDSHIP_SELF", "You cannot befriend yourself");
        }
        Friendship friendship = new Friendship();
        friendship.id = UUID.randomUUID();
        friendship.requesterId = requesterId;
        friendship.addresseeId = addresseeId;
        friendship.status = FriendshipStatus.PENDING;
        friendship.requestedAt = now;
        return friendship;
    }

    /** Only the person invited accepts: the one who asked cannot approve their own request. */
    public void accept(UUID actorId, Instant now) {
        requirePending();
        if (!actorId.equals(addresseeId)) {
            throw new ForbiddenException("FRIEND_REQUEST_NOT_ADDRESSEE", "Only the person invited can accept");
        }
        status = FriendshipStatus.ACCEPTED;
        acceptedAt = now;
    }

    /** Declining (the addressee) and cancelling (the requester) are the same act: the row goes. */
    public void ensureWithdrawableBy(UUID actorId) {
        requirePending();
        requireInvolved(actorId);
    }

    public void ensureUnfriendableBy(UUID actorId) {
        if (status != FriendshipStatus.ACCEPTED) {
            throw new ConflictException("FRIENDSHIP_NOT_ACCEPTED", "You are not friends yet");
        }
        requireInvolved(actorId);
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }

    /** The other person of the pair. Asking about someone outside it is a programming error, not a business case. */
    public UUID otherParty(UUID userId) {
        if (requesterId.equals(userId)) {
            return addresseeId;
        }
        if (addresseeId.equals(userId)) {
            return requesterId;
        }
        throw new IllegalArgumentException("User %s is not part of this friendship".formatted(userId));
    }

    public boolean isRequestedBy(UUID userId) {
        return requesterId.equals(userId);
    }

    /** Key for the pair's advisory lock: the same string whichever id comes first. */
    public static String pairKey(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        return first.compareTo(second) <= 0 ? "friendship:" + first + ":" + second
                : "friendship:" + second + ":" + first;
    }

    private void requirePending() {
        if (status != FriendshipStatus.PENDING) {
            throw new ConflictException("FRIEND_REQUEST_NOT_PENDING", "This request has already been answered");
        }
    }

    private void requireInvolved(UUID actorId) {
        if (!involves(actorId)) {
            throw new ForbiddenException("FRIENDSHIP_NOT_INVOLVED", "This is not your friendship");
        }
    }
}
