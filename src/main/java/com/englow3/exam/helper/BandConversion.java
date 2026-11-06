package com.englow3.exam.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ScoreConversion;
import com.englow3.exam.entity.SectionType;

/**
 * A section's band from its raw score, using the certificate's conversion rows. A band only exists for a section the
 * table was written for - one full section, out of the number of points the table covers, scored in whole points.
 * Anything else (a practice of one part, a TOEIC section at 4.95 a question) has no band rather than a made-up one.
 */
public final class BandConversion {

    private BandConversion() {
    }

    /**
     * @param rows
     *            every conversion row of the paper's certificate
     * @param variant
     *            the paper's variant; a row with no variant holds for all of them, a row naming one wins over it
     * @param scoredOn
     *            the rows in force on this day are used
     */
    public static Optional<BigDecimal> convert(List<ScoreConversion> rows, CertificateVariant variant,
            SectionType sectionType, BigDecimal rawScore, BigDecimal maxRawScore, LocalDate scoredOn) {
        if (rawScore == null || maxRawScore == null) {
            return Optional.empty();
        }
        int raw;
        int max;
        try {
            raw = rawScore.stripTrailingZeros().intValueExact();
            max = maxRawScore.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException notWholePoints) {
            return Optional.empty();
        }
        List<ScoreConversion> table = rows.stream().filter(row -> row.getSectionType() == sectionType)
                .filter(row -> row.getCertificateVariant() == null || row.getCertificateVariant() == variant)
                .filter(row -> !row.getEffectiveFrom().isAfter(scoredOn)).toList();
        Optional<LocalDate> inForce = table.stream().map(ScoreConversion::getEffectiveFrom)
                .max(Comparator.naturalOrder());
        if (inForce.isEmpty()) {
            return Optional.empty();
        }
        List<ScoreConversion> current = table.stream().filter(row -> row.getEffectiveFrom().equals(inForce.get()))
                .toList();
        boolean named = current.stream().anyMatch(row -> row.getCertificateVariant() != null);
        List<ScoreConversion> chosen = current.stream().filter(row -> named == (row.getCertificateVariant() != null))
                .toList();
        int tableMax = chosen.stream().mapToInt(ScoreConversion::getRawScoreTo).max().orElse(-1);
        if (tableMax != max) {
            return Optional.empty();
        }
        return chosen.stream().filter(row -> row.covers(raw)).map(ScoreConversion::getConvertedScore).findFirst();
    }
}
