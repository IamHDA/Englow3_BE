package com.englow3.chat.service;

import java.util.Optional;
import java.util.UUID;

import com.englow3.chat.dto.result.ChatSocketTicket;

/** Single-use tickets for opening the chat socket from a browser, which never holds its own access token. */
public interface ChatSocketTicketService {

    /** A ticket for the signed-in learner. Staff and Admin do not chat, so they are refused. */
    ChatSocketTicket issue();

    /** The learner a ticket belongs to, the first time it is presented before it expires; empty every other time. */
    Optional<UUID> redeem(String ticket);

    /** For the cleanup job: removes tickets that expired long ago. Returns how many. */
    int removeExpired();
}
