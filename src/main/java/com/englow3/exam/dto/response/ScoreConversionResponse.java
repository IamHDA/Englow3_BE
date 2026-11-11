package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.dto.result.ScoreConversionResult;

public record ScoreConversionResponse(Integer id, SectionType sectionType, int rawScoreFrom, int rawScoreTo,
        BigDecimal convertedScore, LocalDate effectiveFrom, String sourceReference) {
    public static ScoreConversionResponse from(ScoreConversionResult r) {
        return new ScoreConversionResponse(r.id(), r.sectionType(), r.rawScoreFrom(), r.rawScoreTo(),
                r.convertedScore(), r.effectiveFrom(), r.sourceReference());
    }
}
