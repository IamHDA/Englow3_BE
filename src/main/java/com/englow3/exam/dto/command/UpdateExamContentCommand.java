package com.englow3.exam.dto.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.request.UpdateExamContentRequest;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.SkillType;

/** No validation annotations - those already ran on {@link UpdateExamContentRequest} before this is built. */
public record UpdateExamContentCommand(UUID examId, List<SectionCommand> sections) {

    public static UpdateExamContentCommand of(UUID examId, UpdateExamContentRequest request) {
        return new UpdateExamContentCommand(examId, request.sections().stream().map(SectionCommand::of).toList());
    }

    public record SectionCommand(SectionType sectionType, int orderNo, BigDecimal maxRawScore, boolean scoredByCriteria,
            Integer timeLimitSeconds, List<PartCommand> parts) {

        public static SectionCommand of(UpdateExamContentRequest.SectionRequest r) {
            return new SectionCommand(r.sectionType(), r.orderNo(), r.maxRawScore(), r.scoredByCriteria(),
                    r.timeLimitSeconds(), r.parts().stream().map(PartCommand::of).toList());
        }
    }

    public record PartCommand(int orderNo, String title, String instruction, String content, String audioObjectKey,
            String imageObjectKey, List<QuestionSetCommand> questionSets) {

        public static PartCommand of(UpdateExamContentRequest.PartRequest r) {
            return new PartCommand(r.orderNo(), r.title(), r.instruction(), r.content(), r.audioObjectKey(),
                    r.imageObjectKey(), r.questionSets().stream().map(QuestionSetCommand::of).toList());
        }
    }

    public record QuestionSetCommand(String title, String instruction, int orderNo, String content,
            String audioObjectKey, String imageObjectKey, UUID sourceQuestionSetId, List<QuestionCommand> questions) {

        public static QuestionSetCommand of(UpdateExamContentRequest.QuestionSetRequest r) {
            return new QuestionSetCommand(r.title(), r.instruction(), r.orderNo(), r.content(), r.audioObjectKey(),
                    r.imageObjectKey(), r.sourceQuestionSetId(),
                    r.questions().stream().map(QuestionCommand::of).toList());
        }
    }

    public record QuestionCommand(QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore, String explanation,
            UUID sourceQuestionId, List<OptionCommand> options) {

        public static QuestionCommand of(UpdateExamContentRequest.QuestionRequest r) {
            return new QuestionCommand(r.questionType(), r.content(), r.difficultyLevel(), r.skillType(),
                    r.questionCategory(), r.orderNo(), r.maxRawScore(), r.explanation(), r.sourceQuestionId(),
                    r.options().stream().map(OptionCommand::of).toList());
        }
    }

    public record OptionCommand(String content, int orderNo, boolean correct, String explanation) {

        public static OptionCommand of(UpdateExamContentRequest.OptionRequest r) {
            return new OptionCommand(r.content(), r.orderNo(), r.correct(), r.explanation());
        }
    }
}
