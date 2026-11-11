package com.englow3.exam.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.englow3.exam.entity.SectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ConfigureToeicScoresRequest(@NotNull LocalDate effectiveFrom,
        @NotBlank @Size(max = 1000) String sourceReference,
        @NotNull @Size(min = 2, max = 202) List<@NotNull @Valid Row> rows) {
    public record Row(@NotNull SectionType sectionType, @Min(0) @Max(100) int rawScoreFrom,
            @Min(0) @Max(100) int rawScoreTo, @NotNull BigDecimal convertedScore) {
    }
}
