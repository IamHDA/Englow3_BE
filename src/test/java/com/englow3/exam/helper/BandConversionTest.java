package com.englow3.exam.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ScoreConversion;
import com.englow3.exam.entity.SectionType;

class BandConversionTest {

    private static final LocalDate JAN = LocalDate.parse("2026-01-01");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-06");
    private static final BigDecimal FORTY = new BigDecimal("40");

    private static ScoreConversion row(CertificateVariant variant, SectionType section, int from, int to, String band,
            LocalDate effective) {
        return ScoreConversion.of(CertificateType.IELTS, variant, section, from, to, new BigDecimal(band), effective);
    }

    private final List<ScoreConversion> rows = List.of(row(null, SectionType.LISTENING, 0, 29, "6.5", JAN),
            row(null, SectionType.LISTENING, 30, 40, "7.0", JAN),
            row(CertificateVariant.ACADEMIC, SectionType.READING, 0, 29, "6.5", JAN),
            row(CertificateVariant.ACADEMIC, SectionType.READING, 30, 40, "7.0", JAN));

    private BigDecimal convert(List<ScoreConversion> table, CertificateVariant variant, SectionType section, String raw,
            BigDecimal max) {
        return BandConversion.convert(table, variant, section, new BigDecimal(raw), max, TODAY).orElse(null);
    }

    @Test
    void aRowWithNoVariantHoldsForEveryVariant() {
        assertThat(convert(rows, CertificateVariant.GENERAL, SectionType.LISTENING, "30", FORTY))
                .isEqualByComparingTo("7.0");
    }

    @Test
    void aSectionScoredOutsideItsVariantsTableHasNoBand() {
        assertThat(convert(rows, CertificateVariant.GENERAL, SectionType.READING, "30", FORTY)).isNull();
    }

    @Test
    void aSectionNotOutOfTheTablesTotalHasNoBand() {
        assertThat(convert(rows, CertificateVariant.ACADEMIC, SectionType.READING, "10", new BigDecimal("13")))
                .isNull();
    }

    @Test
    void aScoreInPartPointsHasNoBand() {
        assertThat(convert(rows, CertificateVariant.ACADEMIC, SectionType.READING, "29.5", FORTY)).isNull();
    }

    @Test
    void wholePointsWrittenWithDecimalsStillConvert() {
        assertThat(BandConversion.convert(rows, CertificateVariant.ACADEMIC, SectionType.READING,
                new BigDecimal("30.00"), new BigDecimal("40.00"), TODAY))
                        .hasValueSatisfying(band -> assertThat(band).isEqualByComparingTo("7.0"));
    }

    @Test
    void theLatestRowsInForceReplaceOlderOnesAndFutureOnesAreIgnored() {
        List<ScoreConversion> revised = new java.util.ArrayList<>(rows);
        revised.add(row(null, SectionType.LISTENING, 0, 40, "5.0", LocalDate.parse("2026-06-01")));
        revised.add(row(null, SectionType.LISTENING, 0, 40, "1.0", LocalDate.parse("2027-01-01")));

        assertThat(convert(revised, CertificateVariant.ACADEMIC, SectionType.LISTENING, "30", FORTY))
                .isEqualByComparingTo("5.0");
    }
}
