package com.englow3.friendship.dto.result;

/** {@code created} is true only when this call wrote a new row; resending or answering a crossed request is not. */
public record FriendRequestOutcome(FriendRequestResult request, boolean created) {
}
