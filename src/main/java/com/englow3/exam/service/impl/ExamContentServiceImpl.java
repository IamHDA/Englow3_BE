package com.englow3.exam.service.impl;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.projection.AdminExamPaperProjection;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Part;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Section;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamMediaResult;
import com.englow3.exam.dto.result.QuestionOptionResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamSection;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSet;
import com.englow3.exam.entity.SectionPart;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetRepository;
import com.englow3.exam.repository.SectionPartRepository;
import com.englow3.exam.service.ExamContentService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.PresignedUrlResolver;

/** Exam content tree and media authoring. */
@Service
public class ExamContentServiceImpl implements ExamContentService {

    private static final Map<String, String> ALLOWED_MEDIA_TYPES = Map.of("audio/mpeg", "mp3", "audio/wav", "wav",
            "image/jpeg", "jpg", "image/png", "png");

    private final ExamRepository examRepo;
    private final ExamSectionRepository examSectionRepo;
    private final SectionPartRepository sectionPartRepo;
    private final QuestionSetRepository questionSetRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository questionOptionRepo;
    private final AdminExamPaperQuery examPaperQuery;
    private final ObjectStorageClient objectStorage;
    private final PresignedUrlResolver presignedUrls;
    private final String examBucket;
    private final Duration mediaUrlTtl;

    public ExamContentServiceImpl(ExamRepository examRepo, ExamSectionRepository examSectionRepo,
            SectionPartRepository sectionPartRepo, QuestionSetRepository questionSetRepo,
            QuestionRepository questionRepo, QuestionOptionRepository questionOptionRepo,
            AdminExamPaperQuery examPaperQuery, ObjectStorageClient objectStorage, PresignedUrlResolver presignedUrls,
            @Value("${app.storage.exam-bucket}") String examBucket,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl) {
        this.examRepo = examRepo;
        this.examSectionRepo = examSectionRepo;
        this.sectionPartRepo = sectionPartRepo;
        this.questionSetRepo = questionSetRepo;
        this.questionRepo = questionRepo;
        this.questionOptionRepo = questionOptionRepo;
        this.examPaperQuery = examPaperQuery;
        this.objectStorage = objectStorage;
        this.presignedUrls = presignedUrls;
        this.examBucket = examBucket;
        this.mediaUrlTtl = mediaUrlTtl;
    }

    public ExamMediaResult uploadMedia(UUID examId, MultipartFile file) {
        // The paper first: media can only ever be attached to an editable one, so a file for a missing or published
        // paper would be an orphan in the bucket that nothing points at and nothing cleans up.
        requireExam(examId).requireEditable();
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("MEDIA_REQUIRED", "No media file was uploaded");
        }
        String contentType = file.getContentType();
        String extension = ALLOWED_MEDIA_TYPES.get(contentType);
        if (extension == null) {
            throw new BadRequestException("MEDIA_TYPE_NOT_SUPPORTED",
                    "Only MP3, WAV, JPEG and PNG media are accepted, got %s".formatted(contentType));
        }
        String subfolder = contentType.startsWith("audio/") ? "audios" : "images";
        String objectKey = "%s/%s/%s.%s".formatted(examId, subfolder, UUID.randomUUID(), extension);
        try {
            objectStorage.upload(examBucket, objectKey, file.getInputStream(), file.getSize(), contentType);
        } catch (IOException e) {
            throw new BadRequestException("MEDIA_UNREADABLE", "The uploaded media could not be read");
        }
        return new ExamMediaResult(objectKey);
    }

    @Transactional
    public ExamDetailResult replaceContent(UpdateExamContentCommand command) {
        Exam exam = requireExam(command.examId());
        exam.requireEditable();
        validateContentTree(exam.getId(), command.sections());

        deleteContent(exam.getId());
        createContent(exam.getId(), command.sections());

        return toResult(examPaperQuery.loadForAdmin(exam.getId()).orElseThrow(() -> examNotFound(exam.getId())));
    }

    private void deleteContent(UUID examId) {
        questionOptionRepo.deleteAllForExam(examId);
        questionRepo.deleteAllForExam(examId);
        questionSetRepo.deleteAllForExam(examId);
        sectionPartRepo.deleteAllForExam(examId);
        examSectionRepo.deleteAllForExam(examId);
    }

    private void createContent(UUID examId, List<UpdateExamContentCommand.SectionCommand> sections) {
        List<ExamSection> sectionRows = new ArrayList<>();
        List<SectionPart> partRows = new ArrayList<>();
        List<QuestionSet> questionSetRows = new ArrayList<>();
        List<Question> questionRows = new ArrayList<>();
        List<QuestionOption> optionRows = new ArrayList<>();

        for (UpdateExamContentCommand.SectionCommand s : sections) {
            ExamSection section = ExamSection.create(examId, s.sectionType(), s.orderNo(), s.maxRawScore(),
                    s.scoredByCriteria(), s.timeLimitSeconds());
            sectionRows.add(section);

            for (UpdateExamContentCommand.PartCommand p : s.parts()) {
                SectionPart part = SectionPart.create(section.getId(), p.orderNo(), p.title(), p.instruction(),
                        p.content(), p.audioObjectKey(), p.imageObjectKey());
                partRows.add(part);

                for (UpdateExamContentCommand.QuestionSetCommand qs : p.questionSets()) {
                    QuestionSet questionSet = QuestionSet.create(part.getId(), qs.title(), qs.instruction(),
                            qs.orderNo(), qs.content(), qs.audioObjectKey(), qs.imageObjectKey(),
                            qs.sourceQuestionSetId());
                    questionSetRows.add(questionSet);

                    for (UpdateExamContentCommand.QuestionCommand q : qs.questions()) {
                        Question question = Question.create(questionSet.getId(), q.questionType(), q.content(),
                                q.difficultyLevel(), q.skillType(), q.questionCategory(), q.orderNo(), q.maxRawScore(),
                                q.explanation(), q.sourceQuestionId());
                        questionRows.add(question);

                        for (UpdateExamContentCommand.OptionCommand o : q.options()) {
                            optionRows.add(QuestionOption.create(question.getId(), o.content(), o.orderNo(),
                                    o.correct(), o.explanation()));
                        }
                    }
                }
            }
        }

        examSectionRepo.saveAll(sectionRows);
        sectionPartRepo.saveAll(partRows);
        questionSetRepo.saveAll(questionSetRows);
        questionRepo.saveAll(questionRows);
        questionOptionRepo.saveAll(optionRows);
    }

    private void validateContentTree(UUID examId, List<UpdateExamContentCommand.SectionCommand> sections) {
        String prefix = examId + "/";
        requireUniqueOrderNo("section", sections, UpdateExamContentCommand.SectionCommand::orderNo);
        for (UpdateExamContentCommand.SectionCommand section : sections) {
            requireUniqueOrderNo("part", section.parts(), UpdateExamContentCommand.PartCommand::orderNo);
            for (UpdateExamContentCommand.PartCommand part : section.parts()) {
                requireOwnedKey(prefix, part.audioObjectKey());
                requireOwnedKey(prefix, part.imageObjectKey());
                requireUniqueOrderNo("question set", part.questionSets(),
                        UpdateExamContentCommand.QuestionSetCommand::orderNo);
                for (UpdateExamContentCommand.QuestionSetCommand questionSet : part.questionSets()) {
                    requireOwnedKey(prefix, questionSet.audioObjectKey());
                    requireOwnedKey(prefix, questionSet.imageObjectKey());
                    requireUniqueOrderNo("question", questionSet.questions(),
                            UpdateExamContentCommand.QuestionCommand::orderNo);
                    for (UpdateExamContentCommand.QuestionCommand question : questionSet.questions()) {
                        requireUniqueOrderNo("option", question.options(),
                                UpdateExamContentCommand.OptionCommand::orderNo);
                    }
                }
            }
        }
    }

    private static <T> void requireUniqueOrderNo(String level, List<T> items, ToIntFunction<T> orderNo) {
        long distinctCount = items.stream().mapToInt(orderNo).distinct().count();
        if (distinctCount != items.size()) {
            throw new BadRequestException("DUPLICATE_ORDER_NO",
                    "Two %ss under the same parent share an order_no".formatted(level));
        }
    }

    private static void requireOwnedKey(String prefix, String objectKey) {
        if (objectKey != null && !objectKey.startsWith(prefix)) {
            throw new BadRequestException("MEDIA_KEY_NOT_IN_EXAM",
                    "Media key %s does not belong to this exam".formatted(objectKey));
        }
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

    private Exam requireExam(UUID examId) {
        return examRepo.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private static NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId));
    }
}
