package com.englow3.chat.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * What a chat socket ticket is made of. Pure: the random source is passed in, so the shape can be checked without
 * touching the database or the clock.
 */
public final class SocketTickets {

    private static final int TICKET_BYTES = 32;

    /** A hash is 64 hex characters, and no ticket of ours is longer than that either. */
    private static final int MAX_LENGTH = 64;

    private SocketTickets() {
    }

    /** 32 random bytes from the given source, base64url without padding: 43 characters. */
    public static String newTicket(SecureRandom random) {
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 of the UTF-8 ticket: 64 characters. Only this is ever stored. */
    public static String hash(String ticket) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java runtime has SHA-256.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /** A ticket that cannot be one of ours: null, blank, or longer than 64 characters. Refused without a query. */
    public static boolean isPlausible(String ticket) {
        return ticket != null && !ticket.isBlank() && ticket.length() <= MAX_LENGTH;
    }
}
