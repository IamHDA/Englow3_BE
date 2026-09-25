package com.englow3.shared.persistence;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Runs a screen's independent read queries at the same time instead of one after another.
 * <p>
 * The database is a region away from the application, so every query is a round trip of 100-200 ms whatever it costs to
 * execute. A dashboard that asks nine independent questions in sequence spends most of two seconds waiting on the
 * network; asked together, it waits about as long as the slowest one. Each read takes its own pooled connection, so the
 * pool size is what bounds how many run at once - a read that finds the pool busy waits for it as any other query
 * would.
 * <p>
 * Only for reads that do not need to see one snapshot: each runs in its own autocommit connection, not in the caller's
 * transaction. A caller must therefore not be {@code @Transactional} itself, or it would hold one connection idle while
 * its reads queue for the rest.
 */
@Component
public class ParallelReads {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** Starts the read now; {@link Read#get()} waits for its answer. */
    public <T> Read<T> fork(Supplier<T> query) {
        return new Read<>(CompletableFuture.supplyAsync(query, executor));
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    /** A read under way. */
    public static final class Read<T> {

        private final CompletableFuture<T> future;

        private Read(CompletableFuture<T> future) {
            this.future = future;
        }

        /**
         * The answer, or the read's own exception - not wrapped, so a failed query surfaces exactly as it would have
         * run inline.
         */
        public T get() {
            try {
                return future.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                if (e.getCause() instanceof Error error) {
                    throw error;
                }
                throw e;
            }
        }
    }
}
