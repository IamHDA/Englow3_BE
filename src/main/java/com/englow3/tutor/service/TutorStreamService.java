package com.englow3.tutor.service;

import java.util.UUID;
import java.util.function.Consumer;
import com.englow3.tutor.dto.result.TutorMessageResult;

public interface TutorStreamService {
    AutoCloseable subscribe(UUID conversationId, UUID messageId, String cursor, Consumer<Event> consumer,
            Runnable finish);

    record Event(String type, String id, String delta, TutorMessageResult message) {
    }
}
