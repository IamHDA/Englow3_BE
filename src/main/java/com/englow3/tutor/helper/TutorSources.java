package com.englow3.tutor.helper;

import java.util.List;
import com.englow3.tutor.dto.result.TutorSourceResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Parses only the application's own persisted source snapshots, never executable markup. */
public final class TutorSources {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TutorSources() {
    }

    public static String cited(String json, String content) {
        try {
            return MAPPER.writeValueAsString(
                    parse(json).stream().filter(source -> content.contains("[" + source.label() + "]")).toList());
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static List<TutorSourceResult> parse(String json) {
        if (json == null || json.isBlank())
            return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<TutorSourceResult>>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            throw new IllegalStateException("Invalid tutor source snapshot", malformed);
        }
    }
}
