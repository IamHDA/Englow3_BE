package com.englow3.tutor.service.impl;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.tutor.dto.result.TutorMessageResult;
import com.englow3.tutor.entity.TutorMessageStatus;
import com.englow3.tutor.repository.TutorConversationRepository;
import com.englow3.tutor.repository.TutorMessageRepository;
import com.englow3.tutor.service.TutorStreamService;
import com.englow3.user.api.UserDirectory;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/** Authorized subscriptions replay the durable partial reply. They never enqueue provider work. */
@Service
@RequiredArgsConstructor
public class TutorStreamServiceImpl implements TutorStreamService {
    private final TutorConversationRepository conversationRepo;
    private final TutorMessageRepository messageRepo;
    private final UserDirectory users;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private final Map<UUID, Integer> connections = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    @Override
    public AutoCloseable subscribe(UUID conversationId, UUID messageId, String cursor, Consumer<Event> consumer,
            Runnable finish) {
        UUID user = users.requireCurrentUserId();
        conversationRepo.findByIdAndUserId(conversationId, user)
                .orElseThrow(() -> new NotFoundException("TUTOR_CONVERSATION_NOT_FOUND", "Conversation not found"));
        messageRepo.findByIdAndTutorConversationId(messageId, conversationId)
                .orElseThrow(() -> new NotFoundException("TUTOR_MESSAGE_NOT_FOUND", "Message not found"));
        if (cursor != null && !cursor.isEmpty() && !cursor.matches("[0-9]{1,8}:([a-fA-F0-9-]{36}|-):[0-9]{1,8}"))
            throw new BadRequestException("TUTOR_CURSOR_INVALID", "Invalid stream cursor");
        if (total.incrementAndGet() > 64) {
            total.decrementAndGet();
            throw new ConflictException("TUTOR_STREAM_LIMIT", "Use polling while the stream is busy");
        }
        try {
            connections.compute(user, (id, count) -> {
                if (count != null && count >= 2)
                    throw new ConflictException("TUTOR_STREAM_LIMIT", "Close an extra tutor tab");
                return count == null ? 1 : count + 1;
            });
        } catch (RuntimeException failure) {
            total.decrementAndGet();
            throw failure;
        }
        AtomicBoolean closed = new AtomicBoolean();
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        Runnable close = () -> {
            if (!closed.compareAndSet(false, true))
                return;
            ScheduledFuture<?> task = future.get();
            if (task != null)
                task.cancel(false);
            connections.computeIfPresent(user, (id, count) -> count <= 1 ? null : count - 1);
            total.decrementAndGet();
            finish.run();
        };
        String[] previous = { cursor == null ? "" : cursor };
        long started = System.nanoTime();
        Runnable poll = () -> {
            if (closed.get())
                return;
            if (System.nanoTime() - started > TimeUnit.SECONDS.toNanos(48)) {
                close.run();
                return;
            }
            try {
                var message = messageRepo.findByIdAndTutorConversationId(messageId, conversationId).orElse(null);
                if (message == null) {
                    consumer.accept(new Event("gone", null, null, null));
                    close.run();
                    return;
                }
                String epoch = message.getRevision() + ":"
                        + (message.getStreamEpoch() == null ? "-" : message.getStreamEpoch());
                String text = message.getContent() == null ? "" : message.getContent();
                int offset = 0;
                if (previous[0].startsWith(epoch + ":")) {
                    try {
                        offset = Integer.parseInt(previous[0].substring(epoch.length() + 1));
                    } catch (NumberFormatException invalid) {
                        offset = 0;
                    }
                }
                if (!previous[0].startsWith(epoch + ":") || offset > text.length()) {
                    offset = 0;
                    consumer.accept(new Event("reset", epoch + ":0", "", null));
                }
                if (offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset)))
                    offset--;
                String next = epoch + ":" + text.length();
                if (offset < text.length())
                    consumer.accept(new Event("delta", next, text.substring(offset), null));
                previous[0] = next;
                if (message.getStatus() != TutorMessageStatus.PENDING) {
                    consumer.accept(new Event("done", next, null, TutorMessageResult.of(message)));
                    close.run();
                } else
                    consumer.accept(new Event("ping", next, null, null));
            } catch (RuntimeException failure) {
                close.run();
            }
        };
        try {
            consumer.accept(new Event("ping", null, null, null));
            future.set(executor.scheduleWithFixedDelay(poll, 0, 1, TimeUnit.SECONDS));
            if (closed.get())
                future.get().cancel(false);
        } catch (RuntimeException failure) {
            close.run();
            throw failure;
        }
        return close::run;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
