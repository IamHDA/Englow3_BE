package com.englow3.ai.tutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.ai.foundation.RenderedPrompt;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class TutorService {

    private final TutorConversationRepository conversationRepository;
    private final TutorMessageRepository messageRepository;
    private final TutorFeedbackRepository feedbackRepository;
    private final TutorRetrievalPort retrievalPort;
    private final PromptInjectionDetector injectionDetector;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final UserDirectory userDirectory;
    private final LearnerProfileRepository profileRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    TutorService(TutorConversationRepository conversationRepository, TutorMessageRepository messageRepository,
            TutorFeedbackRepository feedbackRepository, TutorRetrievalPort retrievalPort,
            PromptInjectionDetector injectionDetector, AiPromptService promptService, AiJobService jobService,
            UserDirectory userDirectory, LearnerProfileRepository profileRepository, ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.feedbackRepository = feedbackRepository;
        this.retrievalPort = retrievalPort;
        this.injectionDetector = injectionDetector;
        this.promptService = promptService;
        this.jobService = jobService;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.userDirectory = userDirectory;
    }

    @Transactional
    TutorDtos.ConversationResponse create(String requestedTitle) {
        UUID userId = requireUserId();
        String title = requestedTitle == null || requestedTitle.isBlank() ? "New English conversation"
                : requestedTitle.strip();
        TutorConversation conversation = conversationRepository.save(TutorConversation.start(userId, title));
        return TutorDtos.ConversationResponse.from(conversation, List.of());
    }

    @Transactional(readOnly = true)
    List<TutorDtos.ConversationResponse> list() {
        UUID userId = requireUserId();
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(conversation -> TutorDtos.ConversationResponse.from(conversation, List.of())).toList();
    }

    @Transactional(readOnly = true)
    TutorDtos.ConversationResponse get(UUID conversationId) {
        TutorConversation conversation = requireConversation(conversationId, requireUserId());
        return TutorDtos.ConversationResponse.from(conversation,
                messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId));
    }

    @Transactional
    void archive(UUID conversationId) {
        requireConversation(conversationId, requireUserId()).archive();
    }

    @Transactional
    TutorDtos.SendMessageResponse send(UUID conversationId, TutorDtos.SendMessageRequest request) {
        UUID userId = requireUserId();
        TutorConversation conversation = requireConversation(conversationId, userId);
        if (!conversation.active()) {
            throw new ConflictException("TUTOR_CONVERSATION_ARCHIVED", "Archived conversations are read-only");
        }

        String messageText = request.message().strip();
        TutorMode mode = request.mode() == null ? TutorMode.Q_AND_A : request.mode();
        TutorMessage replay = messageRepository
                .findByConversationIdAndIdempotencyKey(conversationId, request.idempotencyKey()).orElse(null);
        if (replay != null) {
            if (!replay.getContent().equals(messageText) || replay.getMode() != mode) {
                throw new ConflictException("TUTOR_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used for a different tutor request");
            }
            TutorMessage assistant = messageRepository
                    .findByConversationIdAndReplyToMessageId(conversationId, replay.getId()).orElseThrow();
            return response(replay, assistant);
        }
        TutorMessage userMessage = messageRepository
                .save(TutorMessage.user(conversationId, messageText, request.idempotencyKey(), mode));
        TutorMessage assistantMessage = messageRepository.save(
                TutorMessage.pendingAssistant(conversationId, userMessage.getId(), mode, mode.groundingRequired()));

        if (injectionDetector.detected(messageText)) {
            assistantMessage.refuse("I cannot follow instructions that try to override tutor safety rules.",
                    "LEARNER_PROMPT_INJECTION", "PROMPT_INJECTION");
            messageRepository.save(assistantMessage);
            auditRetrieval(userId, conversationId, userMessage.getId(), messageText, mode,
                    new TutorRetrievalPort.RetrievalResult(List.of(), 0, false, true));
            return response(userMessage, assistantMessage);
        }

        TutorRetrievalPort.RetrievalResult retrieval = retrievalPort.retrieve(userId, messageText, 5);
        List<GroundingReference> references = retrieval.references();
        auditRetrieval(userId, conversationId, userMessage.getId(), messageText, mode, retrieval);
        if (mode.groundingRequired() && references.isEmpty()) {
            assistantMessage.refuse(
                    "I do not have enough approved course material to answer that reliably. Please rephrase or ask a staff-reviewed question.",
                    "INSUFFICIENT_APPROVED_CONTEXT", "UNSUPPORTED_CLAIM");
            messageRepository.save(assistantMessage);
            return response(userMessage, assistantMessage);
        }
        refreshSummary(conversationId);
        RenderedPrompt prompt = promptService.render("TUTOR_REPLY",
                Map.of("level", learnerLevel(userId), "mode", mode.name(), "groundingRequired",
                        mode.groundingRequired(), "history", history(conversationId), "context",
                        groundingText(references), "message", messageText));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("assistantMessageId", assistantMessage.getId().toString());
        payload.put("systemPrompt", prompt.systemPrompt());
        payload.put("userPrompt", prompt.userPrompt());
        payload.put("mode", mode.name());
        payload.put("groundingRequired", mode.groundingRequired());
        ArrayNode citations = payload.putArray("references");
        references.forEach(reference -> citations.addObject().put("referenceId", reference.referenceId())
                .put("contentType", reference.contentType()).put("contentId", reference.contentId())
                .put("revision", reference.revision()).put("contentLevel", reference.contentLevel())
                .put("accessScope", reference.accessScope()).put("label", reference.label())
                .put("groundingHash", reference.groundingHash()));

        AiJob job = jobService.submitForCurrentUser(AiCapability.TUTOR, "TUTOR_REPLY", "TUTOR_MESSAGE",
                assistantMessage.getId(), prompt.version(), payload, "TUTOR:" + request.idempotencyKey());
        assistantMessage.attachJob(job.getId(), prompt.version());
        return new TutorDtos.SendMessageResponse(userMessage.getId(), assistantMessage.getId(), job.getId(),
                job.getStatus().name());
    }

    @Transactional
    void feedback(UUID conversationId, UUID messageId, TutorDtos.FeedbackRequest request) {
        UUID userId = requireUserId();
        requireConversation(conversationId, userId);
        TutorMessage message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new NotFoundException("TUTOR_MESSAGE_NOT_FOUND", "Tutor message was not found"));
        if (message.getRole() != TutorMessageRole.ASSISTANT || message.getStatus() != TutorMessageStatus.COMPLETED) {
            throw new BadRequestException("TUTOR_FEEDBACK_NOT_ALLOWED",
                    "Feedback is accepted only for completed assistant messages");
        }
        if (request.rating() == null && request.reportReason() == null) {
            throw new BadRequestException("TUTOR_FEEDBACK_EMPTY", "A rating or report reason is required");
        }
        if (feedbackRepository.findByMessageIdAndUserId(messageId, userId).isPresent()) {
            throw new ConflictException("TUTOR_FEEDBACK_EXISTS", "Feedback already exists for this message");
        }
        feedbackRepository.save(TutorFeedback.create(messageId, userId, request.rating(),
                request.reportReason() == null ? null : request.reportReason().name(), request.comment()));
    }

    private String history(UUID conversationId) {
        List<TutorMessage> recent = new ArrayList<>(messageRepository
                .findTop12ByConversationIdAndStatusOrderByCreatedAtDesc(conversationId, TutorMessageStatus.COMPLETED));
        Collections.reverse(recent);
        String recentText = recent.stream().map(message -> message.getRole() + ": " + message.getContent())
                .reduce((left, right) -> left + "\n" + right).orElse("No earlier messages");
        String summary = jdbcTemplate.queryForObject("select summary from ai_tutor_conversations where id = ?",
                String.class, conversationId);
        return summary == null || summary.isBlank() ? recentText : "Earlier summary:\n" + summary + "\n" + recentText;
    }

    private String groundingText(List<GroundingReference> references) {
        return references.stream()
                .map(reference -> "[%s] %s: %s".formatted(reference.referenceId(), reference.label(), reference.text()))
                .reduce((left, right) -> left + "\n" + right).orElse("No approved context found");
    }

    @Transactional(readOnly = true)
    List<TutorDtos.CitationResponse> citations(UUID conversationId, UUID messageId) {
        UUID userId = requireUserId();
        requireConversation(conversationId, userId);
        messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new NotFoundException("TUTOR_MESSAGE_NOT_FOUND", "Tutor message was not found"));
        return jdbcTemplate.query("""
                select position, content_type, content_id, content_revision, label, grounding_hash
                from ai_tutor_message_citations where message_id = ? order by position
                """,
                (rs, row) -> new TutorDtos.CitationResponse(rs.getInt("position"), rs.getString("content_type"),
                        rs.getString("content_id"), rs.getInt("content_revision"), rs.getString("label"),
                        rs.getString("grounding_hash")),
                messageId);
    }

    private TutorDtos.SendMessageResponse response(TutorMessage userMessage, TutorMessage assistantMessage) {
        String jobStatus = assistantMessage.getStatus() == TutorMessageStatus.PENDING ? "QUEUED"
                : assistantMessage.getStatus().name();
        return new TutorDtos.SendMessageResponse(userMessage.getId(), assistantMessage.getId(),
                assistantMessage.getAiJobId(), jobStatus);
    }

    private void auditRetrieval(UUID userId, UUID conversationId, UUID userMessageId, String query, TutorMode mode,
            TutorRetrievalPort.RetrievalResult result) {
        ArrayNode selected = objectMapper.createArrayNode();
        result.references()
                .forEach(reference -> selected.addObject().put("referenceId", reference.referenceId())
                        .put("contentType", reference.contentType()).put("contentId", reference.contentId())
                        .put("revision", reference.revision()).put("contentLevel", reference.contentLevel())
                        .put("accessScope", reference.accessScope()).put("score", reference.score())
                        .put("groundingHash", reference.groundingHash()));
        jdbcTemplate.update("""
                insert into ai_tutor_retrieval_audits
                    (id, user_id, conversation_id, user_message_id, query_hash, mode, candidate_count,
                     selected_references, embedding_used, injection_detected)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                """, UUID.randomUUID(), userId, conversationId, userMessageId, TutorGroundingService.sha256(query),
                mode.name(), result.candidateCount(), selected.toString(), result.embeddingUsed(),
                result.injectionDetected());
    }

    private void refreshSummary(UUID conversationId) {
        List<String> older = jdbcTemplate.queryForList("""
                select role || ': ' || left(content, 500)
                from ai_tutor_messages
                where conversation_id = ? and status = 'COMPLETED'
                order by created_at desc, id desc offset 12 limit 20
                """, String.class, conversationId);
        if (!older.isEmpty()) {
            Collections.reverse(older);
            String summary = String.join("\n", older);
            if (summary.length() > 4_000) {
                summary = summary.substring(summary.length() - 4_000);
            }
            jdbcTemplate.update("update ai_tutor_conversations set summary = ? where id = ?", summary, conversationId);
        }
    }

    private String learnerLevel(UUID userId) {
        return profileRepository.findByUserId(userId).map(profile -> profile.getCurrentLevel()).map(Enum::name)
                .orElse("A1");
    }

    private TutorConversation requireConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId).orElseThrow(
                () -> new NotFoundException("TUTOR_CONVERSATION_NOT_FOUND", "Tutor conversation was not found"));
    }

    private UUID requireUserId() {
        return userDirectory.requireCurrentUserId();
    }
}
