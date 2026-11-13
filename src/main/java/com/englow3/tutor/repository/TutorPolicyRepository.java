package com.englow3.tutor.repository;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TutorPolicyRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    public static final List<String> MODES = List.of("GENERAL", "CORRECTION", "CONVERSATION", "PRACTICE");

    public record Row(boolean accepting, List<String> modes, long version) {
    }

    public Row read(String scope, boolean lock) {
        return jdbc.sql("select accepting,allowed_modes::text,version from tutor_policy where scope=:scope"
                + (lock ? " for update" : "")).param("scope", scope).query((rs, index) -> {
                    try {
                        return new Row(rs.getBoolean(1), mapper.readValue(rs.getString(2),
                                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                                }), rs.getLong(3));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                        throw new IllegalStateException("Invalid Tutor policy", invalid);
                    }
                }).optional().orElseGet(() -> new Row(true, MODES, 0));
    }

    public void ensure(String scope) {
        jdbc.sql("insert into tutor_policy(scope) values(:scope) on conflict do nothing").param("scope", scope)
                .update();
    }

    public void save(String scope, boolean accepting, List<String> modes) {
        try {
            jdbc.sql(
                    "update tutor_policy set accepting=:accepting,allowed_modes=cast(:modes as jsonb),version=version+1,updated_at=now() where scope=:scope")
                    .param("scope", scope).param("accepting", accepting)
                    .param("modes", mapper.writeValueAsString(modes)).update();
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
