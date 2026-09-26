package com.englow3.exam.dto.projection;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.SkillType;

/** Query-side admin paper tree. Raw media keys are resolved before it becomes an application Result. */
public record AdminExamPaperProjection(Exam exam, List<Section> sections) {

    public record Section(UUID id, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, Integer timeLimitSeconds, List<Part> parts) {
    }

    public record Part(UUID id, int orderNo, String title, String instruction, String content, String audioObjectKey,
            String imageObjectKey, List<QuestionSet> questionSets) {
    }

    public record QuestionSet(UUID id, String title, String instruction, int orderNo, String content,
            String audioObjectKey, String imageObjectKey, UUID sourceQuestionSetId, List<Question> questions) {
    }

    public record Question(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore, String explanation,
            UUID sourceQuestionId, List<Option> options) {
    }

    public record Option(UUID id, String content, int orderNo, boolean correct, String explanation) {
    }
}
