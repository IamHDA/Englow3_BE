package com.englow3.exam.service.impl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
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
import com.englow3.exam.entity.QuestionAcceptedAnswer;
import com.englow3.exam.entity.QuestionMatchingAnswer;
import com.englow3.exam.entity.QuestionSetOption;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.QuestionSet;
import com.englow3.exam.entity.SectionPart;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.ExamSectionRepository;
import com.englow3.exam.repository.QuestionAcceptedAnswerRepository;
import com.englow3.exam.repository.QuestionMatchingAnswerRepository;
import com.englow3.exam.repository.QuestionSetOptionRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionSetRepository;
import com.englow3.exam.repository.SectionPartRepository;
import com.englow3.exam.service.ExamContentService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.AudioFileHeader;
import com.englow3.shared.storage.ImageFileHeader;
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
    private final QuestionAcceptedAnswerRepository acceptedAnswerRepo;
    private final QuestionSetOptionRepository setOptionRepo;
    private final QuestionMatchingAnswerRepository matchingAnswerRepo;
    private final AdminExamPaperQuery examPaperQuery;
    private final ObjectStorageClient objectStorage;
    private final PresignedUrlResolver presignedUrls;
    private final String examBucket;
    private final Duration mediaUrlTtl;

    public ExamContentServiceImpl(ExamRepository examRepo, ExamSectionRepository examSectionRepo,
            SectionPartRepository sectionPartRepo, QuestionSetRepository questionSetRepo,
            QuestionRepository questionRepo, QuestionOptionRepository questionOptionRepo,
            QuestionAcceptedAnswerRepository acceptedAnswerRepo, QuestionSetOptionRepository setOptionRepo,
            QuestionMatchingAnswerRepository matchingAnswerRepo, AdminExamPaperQuery examPaperQuery,
            ObjectStorageClient objectStorage, PresignedUrlResolver presignedUrls,
            @Value("${app.storage.exam-bucket}") String examBucket,
            @Value("${app.storage.exam-media-url-ttl:PT1H}") Duration mediaUrlTtl) {
        this.examRepo = examRepo;
        this.examSectionRepo = examSectionRepo;
        this.sectionPartRepo = sectionPartRepo;
        this.questionSetRepo = questionSetRepo;
        this.questionRepo = questionRepo;
        this.questionOptionRepo = questionOptionRepo;
        this.acceptedAnswerRepo = acceptedAnswerRepo;
        this.setOptionRepo = setOptionRepo;
        this.matchingAnswerRepo = matchingAnswerRepo;
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
        boolean audio = contentType.startsWith("audio/");
        String subfolder = audio ? "audios" : "images";
        String objectKey = "%s/%s/%s.%s".formatted(examId, subfolder, UUID.randomUUID(), extension);
        try {
            // The declared type is the browser's word; the dictation and flashcard uploads already check the bytes.
            var content = new BufferedInputStream(file.getInputStream());
            content.mark(ImageFileHeader.LENGTH);
            byte[] header = content.readNBytes(ImageFileHeader.LENGTH);
            content.reset();
            if (audio ? !AudioFileHeader.matches(header, contentType) : !ImageFileHeader.matches(header, contentType)) {
                throw new BadRequestException("MEDIA_CONTENT_MISMATCH", "The file does not match its declared format");
            }
            objectStorage.upload(examBucket, objectKey, content, file.getSize(), contentType);
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
        exam.touchContent();
        examRepo.flush();

        deleteContent(exam.getId());
        createContent(exam.getId(), command.sections());

        return toResult(examPaperQuery.loadForAdmin(exam.getId()).orElseThrow(() -> examNotFound(exam.getId())));
    }

    private void deleteContent(UUID examId) {
        matchingAnswerRepo.deleteAllForExam(examId);
        acceptedAnswerRepo.deleteAllForExam(examId);
        questionOptionRepo.deleteAllForExam(examId);
        questionRepo.deleteAllForExam(examId);
        setOptionRepo.deleteAllForExam(examId);
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
        List<QuestionAcceptedAnswer> acceptedRows = new ArrayList<>();
        List<QuestionSetOption> setOptionRows = new ArrayList<>();
        List<QuestionMatchingAnswer> matchingRows = new ArrayList<>();

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
                    Map<String, UUID> setOptionByLabel = new HashMap<>();
                    for (UpdateExamContentCommand.SetOptionCommand o : qs.setOptions()) {
                        QuestionSetOption entry = QuestionSetOption.create(questionSet.getId(), o.label().strip(),
                                o.content(), o.orderNo());
                        setOptionRows.add(entry);
                        setOptionByLabel.put(entry.getLabel(), entry.getId());
                    }

                    for (UpdateExamContentCommand.QuestionCommand q : qs.questions()) {
                        Question question = Question.create(questionSet.getId(), q.questionType(), q.content(),
                                q.difficultyLevel(), q.skillType(), q.questionCategory(), q.orderNo(), q.maxRawScore(),
                                q.explanation(), q.sourceQuestionId());
                        questionRows.add(question);

                        for (UpdateExamContentCommand.OptionCommand o : q.options()) {
                            optionRows.add(QuestionOption.create(question.getId(), o.content(), o.orderNo(),
                                    o.correct(), o.explanation()));
                        }
                        for (UpdateExamContentCommand.AcceptedAnswerCommand a : q.acceptedAnswers()) {
                            acceptedRows.add(QuestionAcceptedAnswer.create(question.getId(), a.text().strip(),
                                    a.caseSensitive(), a.orderNo()));
                        }
                        for (String label : q.matchingLabels()) {
                            matchingRows.add(
                                    QuestionMatchingAnswer.of(question.getId(), setOptionByLabel.get(label.strip())));
                        }
                    }
                }
            }
        }

        examSectionRepo.saveAll(sectionRows);
        sectionPartRepo.saveAll(partRows);
        questionSetRepo.saveAll(questionSetRows);
        setOptionRepo.saveAll(setOptionRows);
        questionRepo.saveAll(questionRows);
        questionOptionRepo.saveAll(optionRows);
        acceptedAnswerRepo.saveAll(acceptedRows);
        matchingAnswerRepo.saveAll(matchingRows);
    }

    private void validateContentTree(UUID examId, List<UpdateExamContentCommand.SectionCommand> sections) {
        String prefix = examId + "/";
        requireUniqueOrderNo("section", sections, UpdateExamContentCommand.SectionCommand::orderNo);
        for (UpdateExamContentCommand.SectionCommand section : sections) {
            // The sitting engine scores chosen options only. An essay or a recording needs a rubric and a grader,
            // which the Writing & Speaking module has; authored here it would reach learners as multiple choice.
            if (section.sectionType() == SectionType.WRITING || section.sectionType() == SectionType.SPEAKING) {
                throw new BadRequestException("EXAM_PRODUCTIVE_SECTION",
                        "Writing and Speaking are authored as Writing & Speaking tasks, not as mock exam sections");
            }
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
                    requireUniqueOrderNo("list entry", questionSet.setOptions(),
                            UpdateExamContentCommand.SetOptionCommand::orderNo);
                    Set<String> labels = new HashSet<>();
                    for (UpdateExamContentCommand.SetOptionCommand entry : questionSet.setOptions()) {
                        if (!labels.add(entry.label().strip())) {
                            throw new BadRequestException("DUPLICATE_SET_OPTION_LABEL",
                                    "Two entries of a question set's list share the label %s"
                                            .formatted(entry.label().strip()));
                        }
                    }
                    for (UpdateExamContentCommand.QuestionCommand question : questionSet.questions()) {
                        requireUniqueOrderNo("option", question.options(),
                                UpdateExamContentCommand.OptionCommand::orderNo);
                        requireUniqueOrderNo("accepted answer", question.acceptedAnswers(),
                                UpdateExamContentCommand.AcceptedAnswerCommand::orderNo);
                        requireAnswerShape(question);
                        if (question.matchingLabels().stream().map(String::strip).distinct().count() != question
                                .matchingLabels().size()) {
                            throw new BadRequestException("DUPLICATE_MATCHING_LABEL",
                                    "A matching question names the same list entry twice");
                        }
                        for (String label : question.matchingLabels()) {
                            if (!labels.contains(label.strip())) {
                                throw new BadRequestException("MATCHING_LABEL_NOT_IN_SET",
                                        "Label %s is not an entry of this question set's list"
                                                .formatted(label.strip()));
                            }
                        }
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

    /**
     * A question is answered either by choosing or by typing, never both: options on a TEXT_ENTRY question would be
     * shown to nobody, and accepted spellings on a choice question would grade nothing. Refused on save rather than
     * kept, so the stored paper never disagrees with what the editor shows.
     */
    private static void requireAnswerShape(UpdateExamContentCommand.QuestionCommand question) {
        boolean typed = question.questionType() == QuestionType.TEXT_ENTRY;
        boolean matched = question.questionType() == QuestionType.MATCHING;
        if (typed && !question.options().isEmpty()) {
            throw new BadRequestException("TEXT_QUESTION_HAS_OPTIONS",
                    "A text-entry question is answered by typing; remove its options");
        }
        if (matched && !question.options().isEmpty()) {
            throw new BadRequestException("MATCHING_QUESTION_HAS_OPTIONS",
                    "A matching question chooses from its question set's list; remove its own options");
        }
        if (!matched && !question.matchingLabels().isEmpty()) {
            throw new BadRequestException("CHOICE_QUESTION_HAS_MATCHING_LABELS",
                    "Only a matching question names an entry of the set's list as its answer");
        }
        if (!typed && !question.acceptedAnswers().isEmpty()) {
            throw new BadRequestException("CHOICE_QUESTION_HAS_ACCEPTED_ANSWERS",
                    "Only a text-entry question has accepted answers");
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
                questionSet.sourceQuestionSetId(), questionSet.questions().stream().map(this::toQuestion).toList(),
                questionSet.setOptions().stream()
                        .map(o -> new ExamDetailResult.SetOptionResult(o.id(), o.label(), o.content(), o.orderNo()))
                        .toList());
    }

    private ExamDetailResult.QuestionResult toQuestion(AdminExamPaperProjection.Question question) {
        return new ExamDetailResult.QuestionResult(question.id(), question.questionType(), question.content(),
                question.difficultyLevel(), question.skillType(), question.questionCategory(), question.orderNo(),
                question.maxRawScore(), question.explanation(), question.sourceQuestionId(),
                question.options().stream()
                        .map(option -> new QuestionOptionResult(option.id(), option.content(), option.orderNo(),
                                option.correct(), option.explanation()))
                        .toList(),
                question.acceptedAnswers().stream().map(a -> new ExamDetailResult.AcceptedAnswerResult(a.id(), a.text(),
                        a.caseSensitive(), a.orderNo())).toList(),
                question.matchingLabels());
    }

    private Exam requireExam(UUID examId) {
        return examRepo.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private static NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId));
    }
}
