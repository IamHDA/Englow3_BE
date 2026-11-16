package com.englow3.chat.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.chat.dto.result.ChatSocketTicket;
import com.englow3.chat.entity.ChatSocketTicketRecord;
import com.englow3.chat.helper.SocketTickets;
import com.englow3.chat.repository.ChatSocketTicketRepository;
import com.englow3.chat.service.ChatSocketTicketService;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Issues and redeems the tickets that let a browser open the chat socket. The ticket itself is never stored, logged or
 * returned twice: only its hash is kept, and a presented ticket is hashed and looked up by that.
 */
@Service
@RequiredArgsConstructor
public class ChatSocketTicketServiceImpl implements ChatSocketTicketService {

    /** A browser asks once per connection; this is far above a reconnect loop that is working. */
    static final int MAX_TICKETS_PER_WINDOW = 30;
    static final Duration TICKET_WINDOW = Duration.ofMinutes(1);

    /** Long past the seconds a ticket lives, so nothing still in use is ever swept. */
    static final Duration EXPIRED_TICKET_AGE = Duration.ofHours(1);

    private final UserDirectory userDirectory;
    private final PublicProfileReader publicProfileReader;
    private final ChatSocketTicketRepository ticketRepo;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    private final SecureRandom random = new SecureRandom();

    @Value("${app.chat.socket.ticket-ttl:PT30S}")
    private Duration ticketTtl;

    @Override
    @Transactional
    public ChatSocketTicket issue() {
        UUID me = userDirectory.requireCurrentUserId();
        if (!publicProfileReader.isLearner(me)) {
            throw new ForbiddenException("CHAT_LEARNERS_ONLY", "Only learners can use chat");
        }
        rateLimiter.check(me, "chat-socket-ticket", MAX_TICKETS_PER_WINDOW, TICKET_WINDOW);

        String ticket = SocketTickets.newTicket(random);
        ticketRepo.save(ChatSocketTicketRecord.issue(SocketTickets.hash(ticket), me, Instant.now(clock), ticketTtl));
        return new ChatSocketTicket(ticket, ticketTtl.getSeconds());
    }

    @Override
    @Transactional
    public Optional<UUID> redeem(String ticket) {
        if (!SocketTickets.isPlausible(ticket)) {
            return Optional.empty();
        }
        return ticketRepo.redeem(SocketTickets.hash(ticket), Instant.now(clock));
    }

    @Override
    @Transactional
    public int removeExpired() {
        return ticketRepo.deleteExpiredBefore(Instant.now(clock).minus(EXPIRED_TICKET_AGE));
    }
}
