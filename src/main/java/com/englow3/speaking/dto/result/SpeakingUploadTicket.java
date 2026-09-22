package com.englow3.speaking.dto.result;

import java.util.UUID;

/**
 * Where to put a recording, and for how long that offer stands.
 *
 * @param uploadUrl
 *            a presigned PUT straight to object storage. The browser uploads there rather than through this
 *            application: routing tens of seconds of audio through a request thread buys nothing, since the bytes are
 *            going to storage either way.
 * @param expiresInSeconds
 *            told to the client so it can say "start again" rather than failing on an expired URL with no explanation
 */
public record SpeakingUploadTicket(UUID attemptId, String uploadUrl, String contentType, long expiresInSeconds) {
}
