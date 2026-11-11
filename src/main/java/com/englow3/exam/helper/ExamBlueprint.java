package com.englow3.exam.helper;

import java.math.BigDecimal;
import java.util.*;
import com.englow3.exam.query.ExamBlueprintQuery.Part;
import com.englow3.exam.entity.*;
import com.englow3.shared.error.ConflictException;

public final class ExamBlueprint {
    private ExamBlueprint() {
    }

    public static void validate(CertificateType certificate, int duration, List<Part> parts) {
        if (certificate != CertificateType.IELTS && certificate != CertificateType.TOEIC)
            return;
        var listening = parts.stream().filter(p -> "LISTENING".equals(p.skill())).toList();
        var reading = parts.stream().filter(p -> "READING".equals(p.skill())).toList();
        if (listening.size() != 4 || reading.size() != 3 || parts.size() != 7
                || listening.stream().map(Part::sectionId).distinct().count() != 1
                || reading.stream().map(Part::sectionId).distinct().count() != 1
                || listening.stream().anyMatch(p -> !p.audio() || p.questions() == 0)
                || reading.stream()
                        .anyMatch(p -> (!p.text() && (certificate == CertificateType.IELTS || p.orderNo() != 5))
                                || p.questions() == 0)
                || (certificate == CertificateType.TOEIC && parts.stream().anyMatch(p -> !p.choicesOnly())))
            invalid();
        int[] expected = certificate == CertificateType.IELTS ? new int[] { 10, 10, 10, 10 }
                : new int[] { 6, 25, 39, 30 };
        for (int i = 0; i < 4; i++) {
            var p = listening.get(i);
            if (p.orderNo() != i + 1 || (certificate == CertificateType.IELTS
                    && p.marks().compareTo(BigDecimal.valueOf(expected[i])) != 0))
                invalid();
            if (certificate == CertificateType.TOEIC && p.questions() != expected[i])
                invalid();
        }
        int total = certificate == CertificateType.IELTS ? 40 : 100;
        if (certificate == CertificateType.IELTS && reading.stream().map(Part::marks)
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.valueOf(total)) != 0)
            invalid();
        for (int i = 0; i < 3; i++) {
            var p = reading.get(i);
            if (p.orderNo() != (certificate == CertificateType.IELTS ? i + 1 : i + 5))
                invalid();
            if (certificate == CertificateType.TOEIC && p.questions() != new int[] { 30, 16, 54 }[i])
                invalid();
        }
        int l = listening.get(0).seconds(), r = reading.get(0).seconds();
        if (r != (certificate == CertificateType.IELTS ? 3600 : 4500)
                || (certificate == CertificateType.IELTS ? (l < 1800 || l > 2400) : l != 2700) || duration != l + r)
            invalid();
    }

    private static void invalid() {
        throw new ConflictException("EXAM_BLUEPRINT_INVALID",
                "Check the mock's section/part counts, marks, passages, audio and section time limits");
    }
}
