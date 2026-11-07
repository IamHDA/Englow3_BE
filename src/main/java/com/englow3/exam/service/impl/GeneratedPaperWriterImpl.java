package com.englow3.exam.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.api.GeneratedPaperWriter;
import com.englow3.exam.dto.command.SaveAuthoringCommand;
import com.englow3.exam.dto.command.SubmitExamForReviewCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand.PartCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand.SectionCommand;
import com.englow3.exam.dto.request.SaveAuthoringRequest;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.repository.FullTestRepository;
import com.englow3.exam.service.AdminExamService;
import com.englow3.exam.service.ExamContentService;
import com.englow3.exam.service.ExamReviewService;
import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GeneratedPaperWriterImpl implements GeneratedPaperWriter {

    private final AdminExamService exams;
    private final ExamContentService content;
    private final ExamReviewService reviews;
    private final FullTestRepository fullTestRepo;
    private final ObjectMapper mapper;
    private final Validator validator;

    @Override
    @Transactional
    public UUID createDraft(UUID authorId, String authoringJson) {
        SaveAuthoringRequest request;
        try {
            request = mapper.readValue(authoringJson, SaveAuthoringRequest.class);
        } catch (JsonProcessingException unreadable) {
            throw new BadRequestException("GENERATED_PAPER_UNREADABLE", "The generated paper is not valid JSON");
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<SaveAuthoringRequest> first = violations.iterator().next();
            throw new BadRequestException("GENERATED_PAPER_INVALID",
                    "%s %s".formatted(first.getPropertyPath(), first.getMessage()));
        }
        return exams.saveAuthoring(SaveAuthoringCommand.of(null, request), authorId).id();
    }

    @Override
    public String uploadAudio(UUID examId, byte[] mp3) {
        return content.uploadMedia(examId, mp3, "audio/mpeg").objectKey();
    }

    @Override
    @Transactional
    public void attachListeningAudio(UUID examId, int partOrderNo, String objectKey) {
        var tree = exams.authoringDetail(examId).content();
        List<SectionCommand> sections = tree
                .sections().stream().map(
                        s -> s.sectionType() != SectionType.LISTENING ? s
                                : new SectionCommand(s.sectionType(), s.orderNo(), s.maxRawScore(),
                                        s.scoredByCriteria(), s.timeLimitSeconds(),
                                        s.parts().stream().map(p -> p.orderNo() != partOrderNo ? p
                                                : new PartCommand(p.orderNo(), p.title(), p.instruction(), p.content(),
                                                        objectKey, p.imageObjectKey(), p.questionSets()))
                                                .toList()))
                .toList();
        content.replaceContent(new UpdateExamContentCommand(examId, sections));
    }

    @Override
    @Transactional
    public void submitForReview(UUID examId) {
        reviews.submitForReview(new SubmitExamForReviewCommand(examId));
    }

    @Override
    @Transactional
    public UUID createFullTestDraft(UUID authorId, String title, String description, UUID examId, List<UUID> taskIds) {
        return fullTestRepo
                .save(FullTest.draft(title, description, CertificateVariant.ACADEMIC, examId, taskIds, authorId))
                .getId();
    }
}
