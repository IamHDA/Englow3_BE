package com.englow3.chat.worker;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.chat.service.ChatSocketRegistry;

import lombok.RequiredArgsConstructor;

/** Keeps open chat sockets from being cut for silence by a proxy that drops connections nothing has crossed. */
@Component
@RequiredArgsConstructor
public class ChatSocketPinger {

    private final ChatSocketRegistry registry;

    @Scheduled(fixedDelayString = "${app.chat.socket.ping-interval:25s}")
    public void ping() {
        registry.pingAll();
    }
}
