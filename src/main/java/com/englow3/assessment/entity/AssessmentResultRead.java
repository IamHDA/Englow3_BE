package com.englow3.assessment.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "assessment_result_reads")
@Getter
public class AssessmentResultRead {
    @Id
    private UUID attemptId;
    private long resultVersion;
    private Instant readAt;

    protected AssessmentResultRead() {
    }

    public static AssessmentResultRead of(UUID attemptId, long version, Instant now) {
        var result = new AssessmentResultRead();
        result.attemptId = attemptId;
        result.resultVersion = version;
        result.readAt = now;
        return result;
    }
}
