package com.englow3.ai.foundation;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.ConflictException;

@Service
public class AiUsageService {

    private final JdbcTemplate jdbcTemplate;
    private final AiProperties properties;
    private final Clock clock;

    /**
     * {@code @Autowired} is load-bearing: with two constructors and neither marked, Spring falls back to looking for a
     * no-arg one and the context fails to start. The second constructor exists only so a test can pin the clock.
     */
    @Autowired
    AiUsageService(JdbcTemplate jdbcTemplate, AiProperties properties) {
        this(jdbcTemplate, properties, Clock.systemUTC());
    }

    AiUsageService(JdbcTemplate jdbcTemplate, AiProperties properties, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void reserve(UUID userId) {
        int changed = jdbcTemplate.update("""
                insert into ai_usage_daily (user_id, usage_date, request_count)
                values (?, ?, 1)
                on conflict (user_id, usage_date) do update
                set request_count = ai_usage_daily.request_count + 1
                where ai_usage_daily.request_count < ?
                """, userId, Date.valueOf(LocalDate.now(clock)), properties.dailyRequestLimit());
        if (changed == 0) {
            throw new ConflictException("AI_DAILY_QUOTA_EXCEEDED", "The daily AI request limit has been reached");
        }
    }

    @Transactional
    public void recordUsage(UUID userId, int inputTokens, int outputTokens, BigDecimal estimatedCost) {
        jdbcTemplate.update("""
                update ai_usage_daily
                set input_tokens = input_tokens + ?, output_tokens = output_tokens + ?,
                    estimated_cost = estimated_cost + ?
                where user_id = ? and usage_date = ?
                """, inputTokens, outputTokens, estimatedCost, userId, Date.valueOf(LocalDate.now(clock)));
    }
}
