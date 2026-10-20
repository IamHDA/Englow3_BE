package com.englow3.tutor.controller;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.englow3.tutor.service.TutorStreamService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tutor")
@RequiredArgsConstructor
public class TutorStreamController {
    private final TutorStreamService service;

    @GetMapping(value = "/conversations/{id}/messages/{messageId}/events", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> events(@PathVariable UUID id, @PathVariable UUID messageId,
            @RequestParam(defaultValue = "") String cursor) {
        SseEmitter emitter = new SseEmitter(50000L);
        AtomicReference<AutoCloseable> subscription = new AtomicReference<>();
        Runnable close = () -> {
            AutoCloseable handle = subscription.getAndSet(null);
            if (handle != null)
                try {
                    handle.close();
                } catch (Exception ignored) {
                    /* already disconnected */ }
        };
        emitter.onCompletion(close);
        emitter.onTimeout(() -> {
            close.run();
            emitter.complete();
        });
        emitter.onError(error -> close.run());
        subscription.set(service.subscribe(id, messageId, cursor, event -> {
            try {
                var frame = SseEmitter.event().name(event.type()).data(event);
                if (event.id() != null)
                    frame.id(event.id());
                emitter.send(frame);
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }, emitter::complete));
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-transform").header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
