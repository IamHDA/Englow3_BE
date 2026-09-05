package com.englow3.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.englow3.shared.error.ApiErrorResponse;
import com.englow3.shared.logging.TraceIdFilter;
import com.englow3.shared.security.SupabaseRoleConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The entry point and the denied handler must be declared inside oauth2ResourceServer: declared in
     * exceptionHandling they are overwritten by the resource server's own BearerTokenAuthenticationEntryPoint.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http.cors(Customizer.withDefaults()).csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                                .permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                        .authenticationEntryPoint((request, response, ex) -> writeError(objectMapper, response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid access token is required"))
                        .accessDeniedHandler((request, response, ex) -> writeError(objectMapper, response,
                                HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You are not allowed to perform this action")));
        return http.build();
    }

    /**
     * What makes {@code @PreAuthorize("hasRole('ADMIN')")} work: without a converter the JWT yields no authority and
     * every role check refuses. The role is read from {@code app_metadata}, so no endpoint pays a database lookup to
     * find out who may call it.
     */
    private static JwtAuthenticationConverter roleConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new SupabaseRoleConverter());
        return converter;
    }

    /** Errors raised in the filter chain never reach GlobalExceptionHandler, so the shape is written by hand here. */
    private static void writeError(ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status,
            String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message, TraceIdFilter.current()));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Last-Event-ID"));
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
