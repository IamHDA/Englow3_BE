package com.englow3.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.chat.dto.result.ChatSocketTicket;
import com.englow3.chat.entity.ChatSocketTicketRecord;
import com.englow3.chat.helper.SocketTickets;
import com.englow3.chat.repository.ChatSocketTicketRepository;
import com.englow3.chat.service.impl.ChatSocketTicketServiceImpl;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

class ChatSocketTicketServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final ChatSocketTicketRepository ticketRepo = mock(ChatSocketTicketRepository.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ChatSocketTicketService service = new ChatSocketTicketServiceImpl(userDirectory, publicProfileReader,
            ticketRepo, rateLimiter, clock);

    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ticketTtl", Duration.ofSeconds(30));
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(ticketRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void storesTheHashOfTheTicketItHandsBackAndNeverTheTicket() {
        when(publicProfileReader.isLearner(me)).thenReturn(true);

        ChatSocketTicket issued = service.issue();

        ArgumentCaptor<ChatSocketTicketRecord> saved = ArgumentCaptor.forClass(ChatSocketTicketRecord.class);
        verify(ticketRepo).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SocketTickets.hash(issued.ticket()));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(issued.ticket());
        assertThat(saved.getValue().getUserId()).isEqualTo(me);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plusSeconds(30));
        assertThat(saved.getValue().getUsedAt()).isNull();
        assertThat(issued.ticket()).hasSize(43);
        assertThat(issued.expiresInSeconds()).isEqualTo(30);
        verify(rateLimiter).check(me, "chat-socket-ticket", 30, Duration.ofMinutes(1));
    }

    @Test
    void staffAndAdminGetNoTicketAndNothingIsStored() {
        when(publicProfileReader.isLearner(me)).thenReturn(false);

        assertThatThrownBy(service::issue).isInstanceOfSatisfying(ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("CHAT_LEARNERS_ONLY"));

        verify(ticketRepo, never()).save(any());
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void anImplausibleTicketIsRefusedWithoutAQuery() {
        for (String ticket : new String[] { null, "", "   ", "x".repeat(65) }) {
            assertThat(service.redeem(ticket)).isEmpty();
        }

        verifyNoInteractions(ticketRepo);
    }

    @Test
    void aPlausibleTicketIsRedeemedByItsHashAtTheCurrentTime() {
        UUID owner = UUID.randomUUID();
        String ticket = "t".repeat(43);
        when(ticketRepo.redeem(SocketTickets.hash(ticket), now)).thenReturn(Optional.of(owner));

        assertThat(service.redeem(ticket)).contains(owner);
        verify(ticketRepo).redeem(SocketTickets.hash(ticket), now);
        // Only ever the hash: the ticket itself is never what the database is asked about.
        verify(ticketRepo, never()).redeem(eq(ticket), any());
    }

    @Test
    void aTicketTheDatabaseDoesNotRedeemIsEmpty() {
        when(ticketRepo.redeem(anyString(), any())).thenReturn(Optional.empty());

        assertThat(service.redeem("t".repeat(43))).isEmpty();
    }

    @Test
    void sweepsTicketsThatExpiredMoreThanAnHourAgo() {
        when(ticketRepo.deleteExpiredBefore(now.minus(Duration.ofHours(1)))).thenReturn(3);

        assertThat(service.removeExpired()).isEqualTo(3);
    }
}
