package com.englow3.chat.controller;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.englow3.chat.service.ChatSocketTicketService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Decides who a chat socket belongs to, before it opens. Two ways in, and nothing else:
 * <ul>
 * <li>a Bearer token the security filters have already validated (a mobile app), or</li>
 * <li>a single-use ticket in the query string (a browser, which never holds its token).</li>
 * </ul>
 * Neither: 401, and no socket. Neither the URL nor the ticket is logged. The {@code Origin} check is the handshake
 * registration's, not this class's.
 */
@Component
@RequiredArgsConstructor
public class ChatSocketHandshakeInterceptor implements HandshakeInterceptor {

    /** The session attribute that carries the learner the socket belongs to. */
    public static final String USER_ID = "chat.userId";

    private final ChatSocketTicketService ticketService;
    private final UserDirectory userDirectory;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Map<String, Object> attributes) {
        // A Bearer token settles it: the ticket, if one was sent as well, is not touched.
        Optional<UUID> userId = bearerUser().or(() -> ticketParam(request).flatMap(ticketService::redeem));
        if (userId.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID, userId.get());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
            Exception exception) {
        // Nothing to do once the socket is open.
    }

    private Optional<UUID> bearerUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            return Optional.empty();
        }
        try {
            return Optional.of(userDirectory.requireCurrentUserId());
        } catch (NotFoundException noAccount) {
            // A valid token for an account that no longer exists is no one.
            return Optional.empty();
        }
    }

    private Optional<String> ticketParam(ServerHttpRequest request) {
        return Optional
                .ofNullable(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket"));
    }
}
