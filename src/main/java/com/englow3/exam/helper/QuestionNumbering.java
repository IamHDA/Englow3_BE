package com.englow3.exam.helper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The number a learner sees on each question, worked out once on the server rather than counted by each screen
 * (docs/decisions.md, D-IELTS-5). Numbers follow paper order; a question taking N numbers ("choose TWO") is numbered N
 * in a row. IELTS starts every section again at 1; TOEIC counts on through the whole paper. Always computed over the
 * whole paper, so a practice of one part keeps the numbers that part has in the full test.
 */
public final class QuestionNumbering {

    private QuestionNumbering() {
    }

    /** A question in paper order, with the section it belongs to and how many numbers it takes. */
    public record Slot(UUID questionId, UUID sectionId, int span) {
    }

    public record Numbers(int from, int to) {
    }

    public static Map<UUID, Numbers> assign(List<Slot> slotsInPaperOrder, boolean restartEachSection) {
        Map<UUID, Numbers> numbers = new LinkedHashMap<>();
        int next = 1;
        UUID currentSection = null;
        for (Slot slot : slotsInPaperOrder) {
            if (restartEachSection && currentSection != null && !Objects.equals(currentSection, slot.sectionId())) {
                next = 1;
            }
            currentSection = slot.sectionId();
            int span = Math.max(1, slot.span());
            numbers.put(slot.questionId(), new Numbers(next, next + span - 1));
            next += span;
        }
        return numbers;
    }
}
