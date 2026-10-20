package com.englow3.tutor;

import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;

class TutorStreamClientTest {
    @Test
    void consumesRealSseWithoutCallingASecondCompletion() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/llm/stream", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key")).isEqualTo("test-key");
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            String frames = "data: {\"delta\":\"Use \"}\n\ndata: {\"delta\":\"an.\"}\n\ndata: {\"result\":{\"content\":\"Use an.\",\"model\":\"test\",\"input_tokens\":10,\"output_tokens\":3}}\n\n";
            exchange.getResponseBody().write(frames.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var client = new LlmClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "test",
                    Duration.ofSeconds(2), Duration.ofSeconds(2));
            List<String> partial = new ArrayList<>();
            String response = client.generateStreaming("Teach English", "Explain articles",
                    List.of(new LlmClient.Turn("user", "Explain articles")), 0.3, 100, partial::add);
            assertThat(partial).containsExactly("Use ", "Use an.");
            assertThat(response).contains("Use an.");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingFinalResultIsRetryable() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/llm/stream", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: {\"delta\":\"Partial\"}\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var client = new LlmClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "test",
                    Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertThatThrownBy(() -> client.generateStreaming("Teach", "Question",
                    List.of(new LlmClient.Turn("user", "Question")), 0.3, 100, text -> {
                    })).isInstanceOf(LlmException.class)
                            .satisfies(error -> assertThat(((LlmException) error).isRetryable()).isTrue());
        } finally {
            server.stop(0);
        }
    }
}
