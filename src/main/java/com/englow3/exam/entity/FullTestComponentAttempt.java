package com.englow3.exam.entity;

import java.util.UUID;
import jakarta.persistence.*;
import lombok.Getter;
import com.englow3.shared.persistence.BasePersistedEntity;
import com.englow3.shared.error.ConflictException;

@Entity
@Table(name = "full_test_component_attempts")
@Getter
public class FullTestComponentAttempt extends BasePersistedEntity {
    @Column(name = "sitting_id", nullable = false)
    private UUID sittingId;
    @Column(nullable = false)
    private String slot;
    @Column(name = "attempt_id")
    private UUID attemptId;

    protected FullTestComponentAttempt() {
    }

    public static FullTestComponentAttempt reserve(UUID sitting, String slot) {
        var result = new FullTestComponentAttempt();
        result.id = UUID.randomUUID();
        result.sittingId = sitting;
        result.slot = slot;
        return result;
    }

    public void bind(UUID attempt) {
        if (attemptId != null && !attemptId.equals(attempt))
            throw new ConflictException("FULL_TEST_PART_ALREADY_STARTED",
                    "Resume the attempt already bound to this part");
        attemptId = attempt;
    }
}
