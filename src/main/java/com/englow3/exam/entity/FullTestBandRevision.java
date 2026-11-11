package com.englow3.exam.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.englow3.shared.persistence.BasePersistedEntity;

@Entity
@Table(name = "full_test_band_revisions")
public class FullTestBandRevision extends BasePersistedEntity {
    private UUID sittingId;
    private int revision;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String previousBands;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String correctedBands;
    private Instant createdAt;

    protected FullTestBandRevision() {
    }

    public static FullTestBandRevision of(FullTestSitting sitting, String previous, String corrected, Instant now) {
        var result = new FullTestBandRevision();
        result.id = UUID.randomUUID();
        result.sittingId = sitting.getId();
        result.revision = sitting.getBandRevision();
        result.previousBands = previous;
        result.correctedBands = corrected;
        result.createdAt = now;
        return result;
    }
}
