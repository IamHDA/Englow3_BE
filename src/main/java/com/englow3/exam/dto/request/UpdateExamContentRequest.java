package com.englow3.exam.dto.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.SkillType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The whole content tree of a paper, replacing everything below {@code exams} in one call - see
 * {@code AdminExamService.replaceContent}. Nested rather than five files, the same choice
 * {@link com.englow3.exam.dto.result.ExamDetailResult} already made: each level is only ever the field type of the
 * level above it.
 * <p>
 * This is also the wizard's autosave, so every list here is {@code @NotNull} but never {@code @NotEmpty} - a section
 * with no part yet, or a question with no option yet, is a normal mid-draft shape, not an error. Only fields the
 * database itself requires not-null carry a per-field constraint ({@code title} on a part, {@code content} on a
 * question or option): a node the caller chooses to send must still be a row the schema can hold. Whether the tree is
 * <em>complete</em> - every question graded, every set of options resolved - is answered once, at
 * {@code Exam.publish(...)}, not here.
 */
public record UpdateExamContentRequest(@NotNull @Valid List<SectionRequest> sections) {

    public record SectionRequest(@NotNull SectionType sectionType, @PositiveOrZero int orderNo,
            @NotNull BigDecimal maxRawScore, boolean scoredByCriteria, Integer timeLimitSeconds,
            @NotNull @Valid List<PartRequest> parts) {
    }

    public record PartRequest(@PositiveOrZero int orderNo, @NotBlank String title, String instruction, String content,
            String audioObjectKey, String imageObjectKey, @NotNull @Valid List<QuestionSetRequest> questionSets) {
    }

    public record QuestionSetRequest(String title, String instruction, @PositiveOrZero int orderNo, String content,
            String audioObjectKey, String imageObjectKey, UUID sourceQuestionSetId,
            @NotNull @Valid List<QuestionRequest> questions) {
    }

    public record QuestionRequest(@NotNull QuestionType questionType, @NotBlank String content,
            @NotNull DifficultyLevel difficultyLevel, @NotNull SkillType skillType, String questionCategory,
            @PositiveOrZero int orderNo, @NotNull BigDecimal maxRawScore, String explanation, UUID sourceQuestionId,
            @NotNull @Valid List<OptionRequest> options) {
    }

    public record OptionRequest(@NotBlank String content, @PositiveOrZero int orderNo, boolean correct,
            String explanation) {
    }
}
