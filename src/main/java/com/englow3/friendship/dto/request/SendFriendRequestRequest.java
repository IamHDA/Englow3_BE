package com.englow3.friendship.dto.request;

import java.util.UUID;

import com.englow3.friendship.dto.command.SendFriendRequestCommand;

import jakarta.validation.constraints.NotNull;

public record SendFriendRequestRequest(@NotNull UUID addresseeId) {

    public SendFriendRequestCommand toCommand() {
        return new SendFriendRequestCommand(addresseeId);
    }
}
