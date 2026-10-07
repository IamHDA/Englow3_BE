package com.englow3.exam.dto.response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englow3.exam.entity.SectionType;
import com.englow3.exam.query.ExamOutlineQuery.OutlinePart;

/** A paper's skills and parts with their question counts - what a practice is picked from. No question content. */
public record ExamOutlineResponse(UUID examId, List<SectionResponse> sections) {

    public record SectionResponse(UUID id, SectionType sectionType, int orderNo, List<PartResponse> parts) {
    }

    public record PartResponse(UUID id, int orderNo, String title, long questionCount) {
    }

    public static ExamOutlineResponse from(UUID examId, List<OutlinePart> parts) {
        Map<UUID, SectionResponse> sections = new LinkedHashMap<>();
        for (OutlinePart part : parts) {
            sections.computeIfAbsent(part.sectionId(),
                    id -> new SectionResponse(id, part.sectionType(), part.sectionOrderNo(), new ArrayList<>())).parts()
                    .add(new PartResponse(part.id(), part.orderNo(), part.title(), part.questionCount()));
        }
        return new ExamOutlineResponse(examId, List.copyOf(sections.values()));
    }
}
