package com.englow3.chat.dto.result;

/** The ticket as the browser must present it. This is the only time it exists in the clear. */
public record ChatSocketTicket(String ticket, long expiresInSeconds) {
}
