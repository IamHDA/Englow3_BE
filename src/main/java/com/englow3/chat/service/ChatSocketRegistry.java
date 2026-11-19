package com.englow3.chat.service;

import java.util.UUID;

import org.springframework.web.socket.WebSocketSession;

import com.englow3.chat.dto.response.ChatSocketFrame;

/**
 * The chat sockets open on this instance, by the learner they belong to. In memory: a push reaches only the sockets
 * held here, which is why chat needs more than this before the backend runs two instances.
 */
public interface ChatSocketRegistry {

    /** Remembers a newly opened socket. Past the per-person limit the oldest one is closed with a policy violation. */
    void add(UUID userId, WebSocketSession session);

    void remove(UUID userId, WebSocketSession session);

    /**
     * To every socket the person has open on this instance. Nobody to send to, or a socket that fails, is not an error.
     */
    void sendTo(UUID userId, ChatSocketFrame frame);

    /** A ping to every open socket, so an idle chat is not cut by a proxy that drops silent connections. */
    void pingAll();
}
