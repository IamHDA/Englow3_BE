package com.englow3.ai.tutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.ai.foundation.RenderedPrompt;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class TutorService {

    private final TutorConversationRepository conversationRepository;
    private final TutorMessageRepository messageRepository;
    private final TutorFeedbackRepository feedbackRepository;
    private final TutorGroundingService groundingService;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final UserRepository userRepository;
    private final LearnerProfileRepository profileRepository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    TutorService(TutorConversationRepository conversationRepository, TutorMessageRepository messageRepository,
            TutorFeedbackRepository feedbackRepository, TutorGroundingService groundingService,
            AiPromptService promptService, AiJobService jobService, UserRepository userRepository,
            LearnerProfileRepository profileRepository, CurrentUser currentUser, ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.feedbackRepository = feedbackRepository;
        this.groundingService = groundingService;
        this.promptService = promptService;
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    TutorDtos.ConversationResponse create(String requestedTitle) {
        User user = requireUser();
        String title = requestedTitle == null || requestedTitle.isBlank() ? "New English conversation"
                : requestedTitle.strip();
        TutorConversation conversation = conversationRepository.save(TutorConversation.start(user.getId(), title));
        return TutorDtos.ConversationResponse.from(conversation, List.of());
    }

    @Transactional(readOnly = true)
    List<TutorDtos.ConversationResponse> list() {
        UUID userId = requireUser().getId();
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(conversation -> TutorDtos.ConversationResponse.from(conversation, List.of())).toList();
    }

    @Transactional(readOnly = true)
    TutorDtos.ConversationResponse get(UUID conversationId) {
        TutorConversation conversation = requireConversation(conversationId, requireUser().getId());
        return TutorDtos.ConversationResponse.from(conversation,
                messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId));
    }

    @Transactional
    void archive(UUID conversationId) {
        requireConversation(conversationId, requireUser().getId()).archive();
    }

    @Transactional
    TutorDtos.SendMessageResponse send(UUID conversationId, TutorDtos.SendMessageRequest request) {
        User user = requireUser();
        TutorConversation conversation = requireConversation(conversationId, user.getId());
        if (!conversation.active()) {
            throw new ConflictException("TUTOR_CONVERSATION_ARCHIVED", "Archived conversations are read-only");
        }

        String messageText = request.message().strip();
        TutorMessage userMessage = messageRepository.save(TutorMessage.user(conversationId, messageText));
        TutorMessage assistantMessage = messageRepository
                .save(TutorMessage.pendingAssistant(conversationId, userMessage.getId()));

        List<GroundingReference> references = groundingService.findApprovedContext(messageText);
        RenderedPrompt prompt = promptService.render("TUTOR_REPLY", Map.of("level", learnerLevel(user.getId()),
                "history", history(conversationId), "context", groundingText(references), "message", messageText));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("assistantMessageId", assistantMessage.getId().toString());
        payload.put("systemPrompt", prompt.systemPrompt());
        payload.put("userPrompt", prompt.userPrompt());
        ArrayNode citations = payload.putArray("citations");
        references.forEach(reference -> citations.addObject().put("contentType", reference.contentType())
                .put("contentId", reference.contentId()).put("label", reference.label()));

        AiJob job = jobService.submitForCurrentUser(AiCapability.TUTOR, "TUTOR_REPLY", "TUTOR_MESSAGE",
                assistantMessage.getId(), prompt.version(), payload, request.idempotencyKey());
        assistantMessage.attachJob(job.getId(), prompt.version());
        return new TutorDtos.SendMessageResponse(userMessage.getId(), assistantMessage.getId(), job.getId(),
                job.getStatus().name());
    }

    @Transactional
    void feedback(UUID conversationId, UUID messageId, TutorDtos.FeedbackRequest request) {
        User user = requireUser();
        requireConversation(conversationId, user.getId());
        TutorMessage message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new NotFoundException("TUTOR_MESSAGE_NOT_FOUND", "Tutor message was not found"));
        if (message.getRole() != TutorMessageRole.ASSISTANT || message.getStatus() != TutorMessageStatus.COMPLETED) {
            throw new BadRequestException("TUTOR_FEEDBACK_NOT_ALLOWED",
                    "Feedback is accepted only for completed assistant messages");
        }
        if (request.rating() == null && request.reportReason() == null) {
            throw new BadRequestException("TUTOR_FEEDBACK_EMPTY", "A rating or report reason is required");
        }
        if (feedbackRepository.findByMessageIdAndUserId(messageId, user.getId()).isPresent()) {
            throw new ConflictException("TUTOR_FEEDBACK_EXISTS", "Feedback already exists for this message");
        }
        feedbackRepository.save(TutorFeedback.create(messageId, user.getId(), request.rating(),
                request.reportReason() == null ? null : request.reportReason().name(), request.comment()));
    }

    private String history(UUID conversationId) {
        List<TutorMessage> recent = new ArrayList<>(messageRepository
                .findTop12ByConversationIdAndStatusOrderByCreatedAtDesc(conversationId, TutorMessageStatus.COMPLETED));
        Collections.reverse(recent);
        return recent.stream().map(message -> message.getRole() + ": " + message.getContent())
                .reduce((left, right) -> left + "\n" + right).orElse("No earlier messages");
    }

    private String groundingText(List<GroundingReference> references) {
        return references.stream()
                .map(reference -> "[%s:%s] %s: %s".formatted(reference.contentType(), reference.contentId(),
                        reference.label(), reference.text()))
                .reduce((left, right) -> left + "\n" + right).orElse("No approved context found");
    }

    private String learnerLevel(UUID userId) {
        return profileRepository.findByUserId(userId).map(profile -> profile.getCurrentLevel()).map(Enum::name)
                .orElse("A1");
    }

    private TutorConversation requireConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId).orElseThrow(
                () -> new NotFoundException("TUTOR_CONVERSATION_NOT_FOUND", "Tutor conversation was not found"));
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }
}
