package com.englow3.roadmap.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * How much each part of the level check counts for learners with one purpose, from one moment on. Rows are never
 * edited: a change is a new row with a later {@code effectiveFrom}, so the history of what was in force stays readable.
 */
@Entity
@Table(name = "level_check_weights")
@Getter
public class LevelCheckWeight extends BasePersistedEntity {

    /** The row for learners whose purposes have none of their own. */
    public static final String DEFAULT_PURPOSE = "DEFAULT";

    private String purposeCode;
    private int vocabulary;
    private int listening;
    private int speaking;
    private int priority;
    private Instant effectiveFrom;
    private UUID createdByUserId;

    protected LevelCheckWeight() {
    }

    /**
     * Percentages that add up to 100, with something on the written side: a check whose spoken part is skipped scales
     * the written weights up to the whole, which is impossible from zero.
     */
    public static LevelCheckWeight from(UUID id, String purposeCode, int vocabulary, int listening, int speaking,
            int priority, Instant effectiveFrom, UUID createdByUserId) {
        if (purposeCode == null || !purposeCode.matches("[A-Z_]{2,30}")) {
            throw invalid("A purpose code is upper-case letters and underscores");
        }
        if (vocabulary < 0 || listening < 0 || speaking < 0 || vocabulary > 100 || listening > 100 || speaking > 100) {
            throw invalid("Each weight is between 0 and 100");
        }
        if (vocabulary + listening + speaking != 100) {
            throw invalid("The three weights must add up to 100");
        }
        if (vocabulary + listening == 0) {
            throw invalid("Vocabulary and listening cannot both be 0");
        }
        if (priority < 0 || priority > 100) {
            throw invalid("Priority is between 0 and 100");
        }
        LevelCheckWeight weight = new LevelCheckWeight();
        weight.id = Objects.requireNonNull(id);
        weight.purposeCode = purposeCode;
        weight.vocabulary = vocabulary;
        weight.listening = listening;
        weight.speaking = speaking;
        weight.priority = priority;
        weight.effectiveFrom = Objects.requireNonNull(effectiveFrom);
        weight.createdByUserId = createdByUserId;
        return weight;
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("LEVEL_CHECK_WEIGHTS_INVALID", message);
    }
}
