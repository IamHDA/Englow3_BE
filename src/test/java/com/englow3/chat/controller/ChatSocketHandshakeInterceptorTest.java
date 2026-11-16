package com.englow3.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

import com.englow3.chat.service.ChatSocketTicketService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

class ChatSocketHandshakeInterceptorTest {

    private final ChatSocketTicketService ticketService = mock(ChatSocketTicketService.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final ChatSocketHandshakeInterceptor interceptor = new ChatSocketHandshakeInterceptor(ticketService,
            userDirectory, publicProfileReader);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);
    private final Map<String, Object> attributes = new HashMap<>();
    private final UUID me = UUID.randomUUID();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private ServerHttpRequest request(String query) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://api.example/ws/chat" + query));
        return request;
    }

    private void bearerSignedIn() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void aTicketThatRedeemsOpensTheSocketForItsOwner() {
        when(ticketService.redeem("good-ticket")).thenReturn(Optional.of(me));

        boolean accepted = interceptor.beforeHandshake(request("?ticket=good-ticket"), response, handler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry("chat.userId", me);
        assertThat(attributes).containsEntry(ChatSocketHandshakeInterceptor.USER_ID, me);
    }

    @Test
    void aTicketThatDoesNotRedeemIsRefusedWith401() {
        when(ticketService.redeem("used-ticket")).thenReturn(Optional.empty());

        boolean accepted = interceptor.beforeHandshake(request("?ticket=used-ticket"), response, handler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    void noTicketAndNoBearerIsRefusedWith401WithoutAskingTheTicketService() {
        boolean accepted = interceptor.beforeHandshake(request(""), response, handler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(ticketService);
    }

    @Test
    void aBearerTokenIsUsedAndTheTicketIsNeverTouched() {
        bearerSignedIn();
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(publicProfileReader.isLearner(me)).thenReturn(true);

        boolean accepted = interceptor.beforeHandshake(request("?ticket=ignored"), response, handler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(ChatSocketHandshakeInterceptor.USER_ID, me);
        verifyNoInteractions(ticketService);
    }

    @Test
    void aBearerTokenForAnAccountThatIsGoneIsNoOneAndFallsBackToTheTicket() {
        bearerSignedIn();
        when(userDirectory.requireCurrentUserId()).thenThrow(new NotFoundException("USER_NOT_FOUND", "gone"));
        when(ticketService.redeem(anyString())).thenReturn(Optional.empty());

        boolean accepted = interceptor.beforeHandshake(request(""), response, handler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aBearerTokenOfSomeoneWhoIsNotALearnerIsNoOneAndFallsBackToTheTicket() {
        bearerSignedIn();
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(publicProfileReader.isLearner(me)).thenReturn(false);
        UUID ticketOwner = UUID.randomUUID();
        when(ticketService.redeem("learner-ticket")).thenReturn(Optional.of(ticketOwner));

        boolean accepted = interceptor.beforeHandshake(request("?ticket=learner-ticket"), response, handler,
                attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(ChatSocketHandshakeInterceptor.USER_ID, ticketOwner);
    }

    @Test
    void aBearerTokenOfSomeoneWhoIsNotALearnerAndNoTicketIsRefusedWith401() {
        bearerSignedIn();
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(publicProfileReader.isLearner(me)).thenReturn(false);

        boolean accepted = interceptor.beforeHandshake(request(""), response, handler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
        verifyNoInteractions(ticketService);
    }
}
