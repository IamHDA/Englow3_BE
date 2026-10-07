package com.englow3.exam.service.impl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.projection.AdminExamPaperProjection;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Part;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Section;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.dto.result.QuestionOptionResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.service.AdminExamService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.UserDirectory;

/** Exam metadata and authoring catalogue. */
@Service
public class AdminExamServiceImpl implements AdminExamService {

    private final ExamRepository examRepo;
    private final AdminExamPaperQuery examPaperQuery;
    private final UserDirectory userDirectory;
    private final PresignedUrlResolver presignedUrls;
    private final com.englow3.exam.service.ExamContentService contentService;
    private final String examBucket;
    private final Duration mediaUrlTtl;

    public AdminExamServiceImpl(ExamRepository examRepo, AdminExamPaperQuery examPaperQuery,
            UserDirectory userDirectory, PresignedUrlResolver presignedUrls,
            @Value("${app.storage.exam-bucket}") String examBucket,
            com.englow3.exam.service.ExamContentService contentService,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl) {
        this.examRepo = examRepo;
        this.examPaperQuery = examPaperQuery;
        this.userDirectory = userDirectory;
        this.presignedUrls = presignedUrls;
        this.examBucket = examBucket;
        this.contentService = contentService;
        this.mediaUrlTtl = mediaUrlTtl;
    }

    @Transactional
    public ExamResult create(CreateExamCommand command) {
        Exam exam = Exam.draft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore(), userDirectory.requireCurrentUserId());
        return ExamResult.of(examRepo.save(exam));
    }

    @Transactional(readOnly = true)
    public Page<ExamListItemResult> search(SearchExamCommand command, Pageable pageable) {
        return examRepo.search(command.status(), command.examType(), command.title(), pageable)
                .map(ExamListItemResult::of);
    }

    @Transactional(readOnly = true)
    public ExamDetailResult detail(ExamDetailCommand command) {
        return toResult(
                examPaperQuery.loadForAdmin(command.examId()).orElseThrow(() -> examNotFound(command.examId())));
    }

    @Transactional
    public ExamResult update(UpdateExamCommand command) {
        Exam exam = requireExam(command.examId());
        exam.updateDraft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore());
        return ExamResult.of(exam);
    }

    private ExamDetailResult toResult(AdminExamPaperProjection paper) {
        return ExamDetailResult.of(paper.exam(), paper.sections().stream().map(this::toSection).toList());
    }

    private ExamDetailResult.AdminSection toSection(Section section) {
        return new ExamDetailResult.AdminSection(section.id(), section.sectionType(), section.orderNo(),
                section.maxRawScore(), section.scoredByCriteria(), section.timeLimitSeconds(),
                section.parts().stream().map(this::toPart).toList());
    }

    private ExamDetailResult.AdminPart toPart(Part part) {
        return new ExamDetailResult.AdminPart(part.id(), part.orderNo(), part.title(), part.instruction(),
                part.content(), presignedUrls.resolve(examBucket, part.audioObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(examBucket, part.imageObjectKey(), mediaUrlTtl),
                part.questionSets().stream().map(this::toQuestionSet).toList());
    }

    private ExamDetailResult.QuestionSetResult toQuestionSet(AdminExamPaperProjection.QuestionSet questionSet) {
        return new ExamDetailResult.QuestionSetResult(questionSet.id(), questionSet.title(), questionSet.instruction(),
                questionSet.orderNo(), questionSet.content(),
                presignedUrls.resolve(examBucket, questionSet.audioObjectKey(), mediaUrlTtl),
                presignedUrls.resolve(examBucket, questionSet.imageObjectKey(), mediaUrlTtl),
                questionSet.sourceQuestionSetId(), questionSet.questions().stream().map(this::toQuestion).toList());
    }

    private ExamDetailResult.QuestionResult toQuestion(AdminExamPaperProjection.Question question) {
        return new ExamDetailResult.QuestionResult(question.id(), question.questionType(), question.content(),
                question.difficultyLevel(), question.skillType(), question.questionCategory(), question.orderNo(),
                question.maxRawScore(), question.explanation(), question.sourceQuestionId(),
                question.options().stream().map(option -> new QuestionOptionResult(option.id(), option.content(),
                        option.orderNo(), option.correct(), option.explanation())).toList());
    }

    @Transactional(readOnly = true)
    public com.englow3.exam.dto.result.AuthoringResult authoringDetail(java.util.UUID id) {
        var paper = examPaperQuery.loadForAdmin(id).orElseThrow(() -> examNotFound(id));
        var e = paper.exam();
        var metadata = new CreateExamCommand(e.getTitle(), e.getDescription(), e.getExamType(), e.getCertificateType(),
                e.getCertificateVariant(), e.getTargetLevel(), e.getDurationSeconds(), e.getMaxRawScore(),
                e.getPassScore());
        var sections = paper.sections().stream()
                .map(s -> new com.englow3.exam.dto.command.UpdateExamContentCommand.SectionCommand(s.sectionType(),
                        s.orderNo(), s.maxRawScore(), s.scoredByCriteria(), s.timeLimitSeconds(),
                        s.parts().stream()
                                .map(p -> new com.englow3.exam.dto.command.UpdateExamContentCommand.PartCommand(
                                        p.orderNo(), p.title(), p.instruction(), p.content(), p.audioObjectKey(),
                                        p.imageObjectKey(),
                                        p.questionSets().stream().map(
                                                qs -> new com.englow3.exam.dto.command.UpdateExamContentCommand.QuestionSetCommand(
                                                        qs.title(), qs.instruction(), qs.orderNo(), qs.content(),
                                                        qs.audioObjectKey(), qs.imageObjectKey(),
                                                        qs.sourceQuestionSetId(),
                                                        qs.questions().stream().map(
                                                                q -> new com.englow3.exam.dto.command.UpdateExamContentCommand.QuestionCommand(
                                                                        q.questionType(), q.content(),
                                                                        q.difficultyLevel(), q.skillType(),
                                                                        q.questionCategory(), q.orderNo(),
                                                                        q.maxRawScore(), q.explanation(),
                                                                        q.sourceQuestionId(),
                                                                        q.options().stream().map(
                                                                                o -> new com.englow3.exam.dto.command.UpdateExamContentCommand.OptionCommand(
                                                                                        o.content(), o.orderNo(),
                                                                                        o.correct(), o.explanation()))
                                                                                .toList()))
                                                                .toList()))
                                                .toList()))
                                .toList()))
                .toList();
        return new com.englow3.exam.dto.result.AuthoringResult(id, e.getAuthoringVersion(), e.getStatus().name(),
                e.getReviewNote(), metadata, new com.englow3.exam.dto.command.UpdateExamContentCommand(id, sections),
                mediaUrls(paper));
    }

    private java.util.Map<String, String> mediaUrls(AdminExamPaperProjection paper) {
        var keys = new java.util.HashSet<String>();
        for (var s : paper.sections())
            for (var p : s.parts()) {
                if (p.audioObjectKey() != null)
                    keys.add(p.audioObjectKey());
                if (p.imageObjectKey() != null)
                    keys.add(p.imageObjectKey());
                for (var q : p.questionSets()) {
                    if (q.audioObjectKey() != null)
                        keys.add(q.audioObjectKey());
                    if (q.imageObjectKey() != null)
                        keys.add(q.imageObjectKey());
                }
            }
        return keys.stream().collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(),
                key -> presignedUrls.resolve(examBucket, key, mediaUrlTtl)));
    }

    @Transactional
    public com.englow3.exam.dto.result.AuthoringResult saveAuthoring(
            com.englow3.exam.dto.command.SaveAuthoringCommand command) {
        java.util.UUID id = command.id();
        if (id == null) {
            id = create(command.metadata()).id();
        } else {
            if (command.version() == null)
                throw new com.englow3.shared.error.BadRequestException("CONTENT_VERSION_REQUIRED",
                        "A version is required when editing");
            var e = requireExam(id);
            e.requireAuthoringVersion(command.version());
            var m = command.metadata();
            e.updateDraft(m.title(), m.description(), m.examType(), m.certificateType(), m.certificateVariant(),
                    m.targetLevel(), m.durationSeconds(), m.maxRawScore(), m.passScore());
        }
        contentService.replaceContent(
                new com.englow3.exam.dto.command.UpdateExamContentCommand(id, command.content().sections()));
        examRepo.flush();
        return authoringDetail(id);
    }

    private Exam requireExam(java.util.UUID examId) {
        return examRepo.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private static NotFoundException examNotFound(java.util.UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId));
    }
}
