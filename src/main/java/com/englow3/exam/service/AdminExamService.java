package com.englow3.exam.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.SearchQuestionBankCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamMediaResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.dto.result.QuestionBankItemResult;
import com.englow3.exam.dto.result.QuestionImportResult;
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
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.spreadsheet.SpreadsheetReader;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.service.UserDirectory;

@Service
public class AdminExamService {

    /** {@code audio/*} goes under "audios", everything else here is an image - see {@code uploadMedia}. */
    private static final Map<String, String> ALLOWED_MEDIA_TYPES = Map.of("audio/mpeg", "mp3", "audio/wav", "wav",
            "image/jpeg", "jpg", "image/png", "png");

    private final ExamRepository examRepo;
    private final ExamSectionRepository examSectionRepo;
    private final SectionPartRepository sectionPartRepo;
    private final QuestionSetRepository questionSetRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository questionOptionRepo;
    private final AdminExamPaperQuery examPaperQuery;
    private final UserDirectory userDirectory;
    private final ObjectStorageClient objectStorage;
    private final String examBucket;
    private final SpreadsheetReader spreadsheetReader;

    AdminExamService(ExamRepository examRepo, ExamSectionRepository examSectionRepo,
            SectionPartRepository sectionPartRepo, QuestionSetRepository questionSetRepo,
            QuestionRepository questionRepo, QuestionOptionRepository questionOptionRepo,
            AdminExamPaperQuery examPaperQuery, UserDirectory userDirectory, ObjectStorageClient objectStorage,
            @Value("${app.storage.exam-bucket}") String examBucket, SpreadsheetReader spreadsheetReader) {
        this.examRepo = examRepo;
        this.examSectionRepo = examSectionRepo;
        this.sectionPartRepo = sectionPartRepo;
        this.questionSetRepo = questionSetRepo;
        this.questionRepo = questionRepo;
        this.questionOptionRepo = questionOptionRepo;
        this.examPaperQuery = examPaperQuery;
        this.userDirectory = userDirectory;
        this.objectStorage = objectStorage;
        this.examBucket = examBucket;
        this.spreadsheetReader = spreadsheetReader;
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

    /** The whole paper with answer keys - what the detail screen renders and what its printed form uses. */
    @Transactional(readOnly = true)
    public ExamDetailResult detail(ExamDetailCommand command) {
        return examPaperQuery.loadForAdmin(command.examId()).orElseThrow(() -> examNotFound(command.examId()));
    }

    /** No {@code save()}: the entity is managed, so the change flushes at commit. */
    @Transactional
    public ExamResult update(UpdateExamCommand command) {
        Exam exam = requireExam(command.examId());
        exam.updateDraft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore());

        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult publish(PublishExamCommand command) {
        Exam exam = requireExam(command.examId());
        exam.publish(examRepo.countSections(exam.getId()), examRepo.countQuestions(exam.getId()),
                examRepo.sumSectionScores(exam.getId()), questionRepo.findIncompleteQuestionOrderNos(exam.getId()),
                Instant.now());

        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult archive(ArchiveExamCommand command) {
        Exam exam = requireExam(command.examId());
        exam.archive();

        return ExamResult.of(exam);
    }

    /**
     * Storage only - no row is written here. {@code examId} is a path segment, not a foreign key: the returned key is
     * only real once the caller places it into a node of {@code PUT /content}'s payload. Follows
     * {@code UserService.upload}'s shape (multipart through the backend, private bucket, content-type decides the
     * extension), except the destination bucket is {@code exam-bucket}, not the public avatar one - a listening
     * recording is the paper, and a stable public link to it is a leaked paper.
     */
    public ExamMediaResult uploadMedia(UUID examId, MultipartFile file) {
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

    /**
     * Parses a CSV/XLSX file into questions - draft only, same as every other authoring entry point. Nothing here is
     * persisted: {@code exam} has one content write path, {@code replaceContent} below, and this stays on the read
     * side of it deliberately (see {@code QuestionImportParser}). The frontend merges the returned rows into the part
     * it is composing and saves them the normal way, through {@code PUT /{id}/content}.
     */
    @Transactional(readOnly = true)
    public QuestionImportResult importQuestions(UUID examId, MultipartFile file) {
        Exam exam = requireExam(examId);
        exam.requireEditable();

        return QuestionImportResult.of(spreadsheetReader.read(file));
    }

    /**
     * Replaces the whole content tree below one paper - draft only, see {@code Exam.requireEditable()}. This is also
     * the wizard's autosave, so only structure is checked here (media keys belong to this exam, no sibling shares an
     * {@code order_no}); whether the tree is complete enough to publish is answered once, by {@code Exam.publish(...)}.
     */
    @Transactional
    public ExamDetailResult replaceContent(UpdateExamContentCommand command) {
        Exam exam = requireExam(command.examId());
        exam.requireEditable();
        validateContentTree(exam.getId(), command.sections());

        deleteContent(exam.getId());
        createContent(exam.getId(), command.sections());

        return examPaperQuery.loadForAdmin(exam.getId()).orElseThrow(() -> examNotFound(exam.getId()));
    }

    /**
     * Bottom-up, and that order is the whole point: every foreign key below {@code exams} is {@code on delete restrict}
     * (V008-V013), so a parent deleted before its children fails. Five bulk statements rather than
     * {@code deleteAll(findAll(...))}, which would load a whole TOEIC paper into memory to delete it row by row.
     * Private, so it runs inside {@code replaceContent}'s transaction - the deletes roll back with the inserts below if
     * anything fails, since {@code @Transactional} on a private method would never apply (Spring's proxy cannot
     * intercept a self-invocation).
     */
    private void deleteContent(UUID examId) {
        questionOptionRepo.deleteAllForExam(examId);
        questionRepo.deleteAllForExam(examId);
        questionSetRepo.deleteAllForExam(examId);
        sectionPartRepo.deleteAllForExam(examId);
        examSectionRepo.deleteAllForExam(examId);
    }

    /**
     * The mirror image: build all five levels first, then save parent-first. The tree can be built before anything is
     * written because each {@code create(...)} assigns its own id, so a child knows its parent's id without waiting for
     * an insert - which is also what lets each level go in one {@code saveAll} rather than a save per row. The five
     * entities extend {@code BasePersistedEntity}, so {@code saveAll} takes the {@code persist} branch directly instead
     * of a {@code SELECT} per row to check whether the id already exists.
     */
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

    @Transactional(readOnly = true)
    public Page<QuestionBankItemResult> searchQuestionBank(SearchQuestionBankCommand command, Pageable pageable) {
        Page<Question> page = questionRepo.search(command.skillType(), command.difficultyLevel(), command.keyword(),
                pageable);
        List<UUID> questionIds = page.getContent().stream().map(Question::getId).toList();
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionIds.isEmpty() ? Map.of()
                : questionOptionRepo.findAllForQuestions(questionIds).stream()
                        .collect(Collectors.groupingBy(QuestionOption::getQuestionId));

        return page.map(q -> QuestionBankItemResult.of(q, optionsByQuestion.getOrDefault(q.getId(), List.of())));
    }

    /**
     * Structure only, per {@code replaceContent}'s own javadoc: a node the caller sends must belong to this exam and
     * must not collide with a sibling's {@code order_no}. An empty list at any level is not checked here - that is a
     * normal mid-draft shape, not a structural error.
     */
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

    /**
     * {@code {examId}/audios|images/...} - see {@code uploadMedia}. Null is not owned by anything and is not an error.
     */
    private static void requireOwnedKey(String prefix, String objectKey) {
        if (objectKey != null && !objectKey.startsWith(prefix)) {
            throw new BadRequestException("MEDIA_KEY_NOT_IN_EXAM",
                    "Media key %s does not belong to this exam".formatted(objectKey));
        }
    }

    private Exam requireExam(UUID examId) {
        return examRepo.findById(examId).orElseThrow(() -> examNotFound(examId));
    }

    private static NotFoundException examNotFound(UUID examId) {
        return new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId));
    }
}
