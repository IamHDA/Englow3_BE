package com.englow3.exam.helper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.englow3.exam.dto.command.ConfigureToeicScoresCommand.Row;
import com.englow3.exam.entity.AttemptSectionResult;
import com.englow3.exam.entity.SectionType;
import com.englow3.shared.error.BadRequestException;

/** The application's TOEIC L/R scale comes only from an explicitly supplied, complete reference table. */
public final class ToeicScoring {
    private ToeicScoring() {
    }

    public static void requireCompleteTable(List<Row> rows) {
        if (rows == null || rows.size() < 2 || rows.size() > 202)
            throw invalid();
        for (Row r : rows)
            if (r == null || r.sectionType() == null
                    || !List.of(SectionType.LISTENING, SectionType.READING).contains(r.sectionType())
                    || r.rawScoreFrom() < 0 || r.rawScoreTo() > 100 || r.rawScoreFrom() > r.rawScoreTo()
                    || !validSectionScore(r.convertedScore()))
                throw invalid();
        for (SectionType section : List.of(SectionType.LISTENING, SectionType.READING)) {
            BigDecimal previous = BigDecimal.ZERO;
            for (int raw = 0; raw <= 100; raw++) {
                final int value = raw;
                List<Row> matches = rows.stream()
                        .filter(r -> r.sectionType() == section && r.rawScoreFrom() <= value && value <= r.rawScoreTo())
                        .toList();
                if (matches.size() != 1 || matches.getFirst().convertedScore().compareTo(previous) < 0)
                    throw invalid();
                previous = matches.getFirst().convertedScore();
            }
        }
    }

    private static boolean validSectionScore(BigDecimal score) {
        return score != null && score.compareTo(BigDecimal.valueOf(5)) >= 0
                && score.compareTo(BigDecimal.valueOf(495)) <= 0
                && score.remainder(BigDecimal.valueOf(5)).signum() == 0;
    }

    private static BadRequestException invalid() {
        return new BadRequestException("TOEIC_CONVERSION_INVALID",
                "Provide non-overlapping, monotonic Listening and Reading ranges covering every correct count 0..100, with scores 5..495 in steps of five");
    }

    public static Optional<BigDecimal> total(List<AttemptSectionResult> sections) {
        if (sections.size() != 2 || sections.stream().map(AttemptSectionResult::getSectionType).distinct().count() != 2
                || sections.stream()
                        .anyMatch(s -> !List.of(SectionType.LISTENING, SectionType.READING).contains(s.getSectionType())
                                || s.getQuestionCount() != 100 || !validSectionScore(s.getConvertedScore())))
            return Optional.empty();
        return Optional.of(sections.stream().map(AttemptSectionResult::getConvertedScore).reduce(BigDecimal.ZERO,
                BigDecimal::add));
    }
}
