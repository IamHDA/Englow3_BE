package com.englow3.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.NotFoundException;

class ParallelReadsTest {

    private final ParallelReads reads = new ParallelReads();

    @AfterEach
    void stop() {
        reads.shutdown();
    }

    @Test
    void returnsEachReadsOwnAnswer() {
        var first = reads.fork(() -> 1);
        var second = reads.fork(() -> "two");

        assertThat(first.get()).isEqualTo(1);
        assertThat(second.get()).isEqualTo("two");
    }

    /** Two reads that each wait for the other can only finish if they really run at the same time. */
    @Test
    void runsTheReadsAtTheSameTime() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        var first = reads.fork(() -> awaitOther(bothStarted));
        var second = reads.fork(() -> awaitOther(bothStarted));

        assertThat(first.get()).isTrue();
        assertThat(second.get()).isTrue();
    }

    /** A failed query surfaces as the query's own exception, so the error handler maps it as it did inline. */
    @Test
    void rethrowsTheReadsOwnException() {
        var read = reads.fork(() -> {
            throw new NotFoundException("THING_NOT_FOUND", "no thing");
        });

        assertThatThrownBy(read::get).isInstanceOf(NotFoundException.class);
    }

    private static boolean awaitOther(CountDownLatch latch) {
        latch.countDown();
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
