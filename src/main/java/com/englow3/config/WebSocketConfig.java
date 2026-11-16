package com.englow3.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.englow3.chat.controller.ChatSocketHandler;
import com.englow3.chat.controller.ChatSocketHandshakeInterceptor;

/**
 * Registers the chat socket. Only where there is a web server, like the security configuration beside it: a one-shot
 * tool such as the pipeline importer runs without one.
 */
@Configuration
@EnableWebSocket
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebSocketConfig implements WebSocketConfigurer {

    private final ChatSocketHandler handler;
    private final ChatSocketHandshakeInterceptor interceptor;
    private final List<String> allowedOrigins;

    WebSocketConfig(ChatSocketHandler handler, ChatSocketHandshakeInterceptor interceptor,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * {@code setAllowedOrigins} refuses a handshake whose {@code Origin} is not in the list the web API's CORS uses
     * (403). A request with no {@code Origin} at all - a mobile app - is let through, which is what it needs.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat").addInterceptors(interceptor)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    /**
     * Only on a real web server. The container's WebSocket limits are set through the server's own
     * {@code ServerContainer}, which exists in a running Tomcat but not in the mock servlet context that
     * {@code @SpringBootTest} uses by default - where this bean would stop the whole context from starting.
     */
    @Bean
    @Conditional(OnRealWebServer.class)
    ServletServerContainerFactoryBean webSocketContainer(
            @Value("${app.chat.socket.max-text-message-bytes:1024}") int maxTextMessageBytes) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // The only client frame is a tiny typing signal.
        container.setMaxTextMessageBufferSize(maxTextMessageBytes);
        container.setMaxBinaryMessageBufferSize(maxTextMessageBytes);
        // Pings every 25 seconds keep a live socket well inside this.
        container.setMaxSessionIdleTimeout(120_000L);
        return container;
    }

    /** True when the application context is the one that started an embedded server. */
    static final class OnRealWebServer implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return context.getResourceLoader() instanceof WebServerApplicationContext;
        }
    }
}
