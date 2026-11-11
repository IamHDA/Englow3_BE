package com.englow3.exam.dto.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.englow3.exam.entity.SectionType;

public record ConfigureToeicScoresCommand(LocalDate effectiveFrom, String sourceReference, List<Row> rows) {
    public record Row(SectionType sectionType, int rawScoreFrom, int rawScoreTo, BigDecimal convertedScore) {
    }
}
