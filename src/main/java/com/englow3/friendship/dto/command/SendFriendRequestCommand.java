package com.englow3.friendship.dto.command;

import java.util.UUID;

public record SendFriendRequestCommand(UUID addresseeId) {
}
