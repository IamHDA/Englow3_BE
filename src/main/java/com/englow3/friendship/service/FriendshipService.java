package com.englow3.friendship.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.friendship.dto.command.SendFriendRequestCommand;
import com.englow3.friendship.dto.result.FriendRequestOutcome;
import com.englow3.friendship.dto.result.FriendRequestResult;
import com.englow3.friendship.dto.result.FriendResult;
import com.englow3.friendship.dto.result.LearnerSearchResult;
import com.englow3.friendship.dto.result.RelationshipResult;

/**
 * Everything the signed-in learner does with friends and friend requests. Page order is fixed by the service; only the
 * page number and size of the {@link Pageable} are used.
 */
public interface FriendshipService {

    /**
     * Asks {@code addresseeId} to be a friend. Asking someone who has already asked the caller accepts that request
     * instead, and asking again while the caller's own request is waiting hands the same request back.
     */
    FriendRequestOutcome sendRequest(SendFriendRequestCommand command);

    FriendRequestOutcome accept(UUID requestId);

    /** Declines the request when the caller is the one invited, cancels it when the caller sent it. */
    void withdraw(UUID requestId);

    void unfriend(UUID otherUserId);

    Page<FriendResult> friends(Pageable page);

    Page<FriendRequestResult> incomingRequests(Pageable page);

    Page<FriendRequestResult> outgoingRequests(Pageable page);

    long incomingRequestCount();

    Page<LearnerSearchResult> searchLearners(String term, Pageable page);

    RelationshipResult relationshipWith(UUID otherUserId);
}
