package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.englow3.exam.entity.SectionType;

public record ScoreConversionResult(Integer id, SectionType sectionType, int rawScoreFrom, int rawScoreTo,
        BigDecimal convertedScore, LocalDate effectiveFrom, String sourceReference) {
}
