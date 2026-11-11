package com.englow3.exam.helper;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.query.ExamBlueprintQuery.Part;

class ExamBlueprintTest {
    private List<Part> ielts() {
        UUID l = UUID.randomUUID(), r = UUID.randomUUID();
        var parts = new ArrayList<Part>();
        for (int i = 0; i < 7; i++)
            parts.add(new Part(i < 4 ? l : r, i < 4 ? "LISTENING" : "READING", i < 4 ? 1920 : 3600,
                    i < 4 ? i + 1 : i - 3, i < 4 ? 10 : i == 6 ? 14 : 13,
                    BigDecimal.valueOf(i < 4 ? 10 : i == 6 ? 14 : 13), true, true));
        return parts;
    }

    @Test
    void acceptsACompleteAcademicPaper() {
        assertThatCode(() -> ExamBlueprint.validate(CertificateType.IELTS, 5520, ielts())).doesNotThrowAnyException();
    }

    @Test
    void refusesMissingAudioEvenWithFortyMarks() {
        var parts = ielts();
        var p = parts.get(0);
        parts.set(0,
                new Part(p.sectionId(), p.skill(), p.seconds(), p.orderNo(), p.questions(), p.marks(), false, true));
        assertThatThrownBy(() -> ExamBlueprint.validate(CertificateType.IELTS, 5520, parts))
                .hasMessageContaining("audio");
    }

    @Test
    void refusesMissingPartsAndWrongTime() {
        assertThatThrownBy(() -> ExamBlueprint.validate(CertificateType.IELTS, 5520, ielts().subList(0, 6)))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> ExamBlueprint.validate(CertificateType.IELTS, 3600, ielts()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void toeicAllowsWeightedRawPointsButRequiresTheCompleteQuestionCounts() {
        UUID l = UUID.randomUUID(), r = UUID.randomUUID();
        var parts = new ArrayList<Part>();
        int[] counts = { 6, 25, 39, 30, 30, 16, 54 };
        for (int i = 0; i < 7; i++)
            parts.add(new Part(i < 4 ? l : r, i < 4 ? "LISTENING" : "READING", i < 4 ? 2700 : 4500, i + 1, counts[i],
                    BigDecimal.valueOf(counts[i]).multiply(new BigDecimal("4.95")), true, i != 4));
        assertThatCode(() -> ExamBlueprint.validate(CertificateType.TOEIC, 7200, parts)).doesNotThrowAnyException();
    }
}
