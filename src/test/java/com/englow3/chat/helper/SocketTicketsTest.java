package com.englow3.chat.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class SocketTicketsTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void aTicketIs43CharactersOfBase64urlWithNoPadding() {
        String ticket = SocketTickets.newTicket(random);

        assertThat(ticket).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void twoTicketsAreNeverTheSame() {
        assertThat(SocketTickets.newTicket(random)).isNotEqualTo(SocketTickets.newTicket(random));
    }

    @Test
    void theHashIsStable64LowerCaseHexAndNotTheTicket() {
        String ticket = SocketTickets.newTicket(random);

        String hash = SocketTickets.hash(ticket);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(SocketTickets.hash(ticket)).isNotEqualTo(ticket);
        assertThat(SocketTickets.hash(ticket + "x")).isNotEqualTo(hash);
    }

    @Test
    void theHashIsTheSha256OfTheUtf8Text() {
        // The well-known SHA-256 of "abc": proves it is plain SHA-256, so a hash can be reproduced outside this code.
        assertThat(SocketTickets.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void aTicketOfOursIsPlausible() {
        assertThat(SocketTickets.isPlausible(SocketTickets.newTicket(random))).isTrue();
        assertThat(SocketTickets.isPlausible("x".repeat(64))).isTrue();
    }

    @Test
    void nullBlankAndOverlongCannotBeOneOfOurs() {
        assertThat(SocketTickets.isPlausible(null)).isFalse();
        assertThat(SocketTickets.isPlausible("")).isFalse();
        assertThat(SocketTickets.isPlausible("   ")).isFalse();
        assertThat(SocketTickets.isPlausible("x".repeat(65))).isFalse();
    }
}
