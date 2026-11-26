package com.englow3.chat.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.service.ChatSocketRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Holds the open sockets in memory. Every socket is wrapped in a {@link ConcurrentWebSocketSessionDecorator}:
 * {@code WebSocketSession.sendMessage} is not safe to call from two threads at once, and a push after a commit and the
 * pinger can both write to one socket. The decorator queues what arrives while a send is in progress, and closes a
 * socket that is too slow to take what it is sent instead of letting it back up the server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSocketRegistryImpl implements ChatSocketRegistry {

    /** How long one send may take before the socket is given up on. */
    static final int SEND_TIME_LIMIT_MS = 5_000;

    /** How much may be queued behind a send in progress before the socket is given up on. */
    static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    /** Oldest first, so the one to close when a person opens too many is the head. */
    private final ConcurrentHashMap<UUID, Deque<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /** The wrapped socket for each raw one, so a close callback that hands back the raw socket finds its wrapper. */
    private final ConcurrentHashMap<String, WebSocketSession> wrappedByRawId = new ConcurrentHashMap<>();

    @Value("${app.chat.socket.max-sessions-per-user:5}")
    private int maxSessionsPerUser;

    @Override
    public void add(UUID userId, WebSocketSession session) {
        WebSocketSession wrapped = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                BUFFER_SIZE_LIMIT_BYTES);
        wrappedByRawId.put(session.getId(), wrapped);
        List<WebSocketSession> evicted = new ArrayList<>();
        sessionsByUser.compute(userId, (id, sessions) -> {
            Deque<WebSocketSession> held = sessions == null ? new ConcurrentLinkedDeque<>() : sessions;
            held.addLast(wrapped);
            while (held.size() > maxSessionsPerUser) {
                evicted.add(held.pollFirst());
            }
            return held;
        });
        // Closed outside the map's lock: closing talks to the network.
        for (WebSocketSession oldest : evicted) {
            wrappedByRawId.remove(oldest.getId());
            close(oldest, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void remove(UUID userId, WebSocketSession session) {
        WebSocketSession wrapped = wrappedByRawId.remove(session.getId());
        if (wrapped != null) {
            forget(userId, wrapped);
        }
    }

    @Override
    public void sendTo(UUID userId, ChatSocketFrame frame) {
        Deque<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message;
        try {
            // Once, however many sockets the person has open.
            message = new TextMessage(objectMapper.writerFor(ChatSocketFrame.class).writeValueAsString(frame));
        } catch (JacksonException failure) {
            log.warn("Could not serialise a chat socket frame of type {}", frame.getClass().getSimpleName(), failure);
            return;
        }
        for (WebSocketSession session : List.copyOf(sessions)) {
            send(userId, session, message);
        }
    }

    @Override
    public void pingAll() {
        PingMessage ping = new PingMessage();
        sessionsByUser.forEach((userId, sessions) -> {
            for (WebSocketSession session : List.copyOf(sessions)) {
                send(userId, session, ping);
            }
        });
    }

    private void send(UUID userId, WebSocketSession session, WebSocketMessage<?> message) {
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException failure) {
            // A socket that cannot be written to is gone or hopelessly slow: close it and forget it. The push that
            // found out is not the sender's problem, and the person catches up over REST when they reconnect.
            log.debug("Dropping a chat socket that could not be written to: {}", failure.toString());
            wrappedByRawId.remove(session.getId());
            forget(userId, session);
            close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void forget(UUID userId, WebSocketSession wrapped) {
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(wrapped);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException failure) {
            log.debug("Could not close a chat socket: {}", failure.toString());
        }
    }
}
