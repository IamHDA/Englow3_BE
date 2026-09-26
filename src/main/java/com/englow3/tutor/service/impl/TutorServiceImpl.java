package com.englow3.tutor.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.tutor.dto.command.ReportTutorMessageCommand;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.dto.result.TutorConversationResult;
import com.englow3.tutor.dto.result.TutorConversationSummaryResult;
import com.englow3.tutor.dto.result.TutorMessageResult;
import com.englow3.tutor.entity.TutorConversation;
import com.englow3.tutor.entity.TutorMessage;
import com.englow3.tutor.entity.TutorMessageRole;
import com.englow3.tutor.entity.TutorMessageStatus;
import com.englow3.tutor.repository.TutorConversationRepository;
import com.englow3.tutor.repository.TutorMessageRepository;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import com.englow3.tutor.service.*;

/**
 * The tutor conversation. Asking is synchronous, answering is not: the question is stored and queued, and the screen
 * polls the turn it created rather than holding a request open for however long a provider takes.
 */
@Service
@RequiredArgsConstructor
public class TutorServiceImpl implements TutorService {

    /** The type name this module's jobs carry. The queue stores it as text and never interprets it. */
    static final String MESSAGE_TARGET_TYPE = "TUTOR_MESSAGE";

    private final TutorConversationRepository conversationRepo;
    private final TutorMessageRepository messageRepo;
    private final AiJobQueue aiJobQueue;
    private final UserDirectory userDirectory;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Stores the question, queues the answer, and hands back both turns.
     * <p>
     * The learner's message is written before anything is asked of a provider. A question lost because the provider was
     * down is a question the learner has to type again, and they would have no way of knowing that is what happened.
     */
    @Transactional
    public TutorConversationResult send(SendTutorMessageCommand command) {
        UUID userId = userDirectory.requireCurrentUserId();
        Instant now = clock.instant();

        TutorConversation conversation = command.conversationId() == null ? null
                : requireOwnConversation(command.conversationId(), userId);
        if (conversation != null && conversation.getArchivedAt() != null) {
            // Archived is gone from the learner's list; writing into it spent their allowance on a thread they
            // could no longer see.
            throw new ConflictException("TUTOR_CONVERSATION_ARCHIVED", "This conversation has been archived");
        }
        List<TutorMessage> existing = conversation == null ? List.of()
                : messageRepo.findByTutorConversationIdOrderByOrderNo(conversation.getId());
        // One question at a time. A second one sent before the first is answered was generated without that answer
        // in its history, and paid for a second reply to a conversation that had not moved on.
        if (existing.stream().anyMatch(message -> message.getStatus() == TutorMessageStatus.PENDING)) {
            throw new ConflictException("TUTOR_REPLY_PENDING",
                    "Wait for the tutor to answer before asking the next question");
        }
        // After the conversation's own checks, so an archived or busy thread says so rather than "limit reached";
        // and before anything is written, so a refusal leaves no empty thread behind.
        requireQuotaRemaining(userId);
        if (conversation == null) {
            conversation = conversationRepo
                    .save(TutorConversation.start(userId, command.message(), command.topic(), now));
        }

        int nextOrderNo = existing.size() + 1;

        TutorMessage question = messageRepo
                .save(TutorMessage.fromLearner(conversation.getId(), nextOrderNo, command.message()));
        TutorMessage answer = messageRepo.save(TutorMessage.awaitingReply(conversation.getId(), nextOrderNo + 1));

        // Both turns count. A learner shown "3 messages" over a thread of six is reading a different conversation
        // from the one they had.
        conversation.recordTurn(now);
        conversation.recordTurn(now);

        aiJobQueue.enqueueTutorReply(answer.getId(), generationRequest(answer.getId(), existing, command.message()),
                // One job per pending turn, whatever the client does. A double send loses at the unique index
                // rather than paying the provider twice for the same question.
                MESSAGE_TARGET_TYPE + ":" + answer.getId(), TutorPrompt.VERSION, userId);

        return new TutorConversationResult(TutorConversationSummaryResult.of(conversation),
                List.of(TutorMessageResult.of(question), TutorMessageResult.of(answer)));
    }

    /** What the screen polls while it waits, and reads once the answer lands. */
    @Transactional(readOnly = true)
    public TutorConversationResult conversation(UUID conversationId) {
        UUID userId = userDirectory.requireCurrentUserId();
        TutorConversation conversation = requireOwnConversation(conversationId, userId);

        return new TutorConversationResult(TutorConversationSummaryResult.of(conversation), messageRepo
                .findByTutorConversationIdOrderByOrderNo(conversationId).stream().map(TutorMessageResult::of).toList());
    }

    @Transactional(readOnly = true)
    public List<TutorConversationSummaryResult> conversations() {
        UUID userId = userDirectory.requireCurrentUserId();

        return conversationRepo.findByUserIdAndArchivedAtIsNullOrderByLastMessageAtDesc(userId).stream()
                .map(TutorConversationSummaryResult::of).toList();
    }

    @Transactional
    public TutorConversationSummaryResult archive(UUID conversationId) {
        UUID userId = userDirectory.requireCurrentUserId();
        TutorConversation conversation = requireOwnConversation(conversationId, userId);
        conversation.archive(clock.instant());

        return TutorConversationSummaryResult.of(conversation);
    }

    /**
     * Records that the learner says an answer is wrong or inappropriate.
     * <p>
     * Stored on the message rather than raised somewhere else, because the answer and the complaint about it are the
     * same row to whoever reviews it. The learner is told nothing changed about the answer itself - hiding it would
     * lose the thing being reported.
     */
    @Transactional
    public TutorMessageResult report(UUID conversationId, UUID messageId, ReportTutorMessageCommand command) {
        UUID userId = userDirectory.requireCurrentUserId();
        requireOwnConversation(conversationId, userId);

        TutorMessage message = messageRepo.findByIdAndTutorConversationId(messageId, conversationId)
                .orElseThrow(() -> new NotFoundException("TUTOR_MESSAGE_NOT_FOUND",
                        "No message with id %s in this conversation".formatted(messageId)));
        message.report(command == null ? null : command.note(), clock.instant());

        return TutorMessageResult.of(message);
    }

    /** The request as it will be sent, stored on the job so an audit can answer what exactly was asked for. */
    private String generationRequest(UUID messageId, List<TutorMessage> history, String question) {
        List<TutorPrompt.Turn> turns = history.stream()
                .map(message -> new TutorPrompt.Turn(message.getRole() == TutorMessageRole.USER, message.getContent()))
                .toList();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("messageId", messageId.toString());
        payload.put("systemPrompt", TutorPrompt.SYSTEM_PROMPT);
        payload.put("userPrompt", TutorPrompt.userPrompt(turns, question));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            // Every value above is a String this method just put in; there is nothing here Jackson can refuse.
            throw new IllegalStateException("Could not serialise a tutor generation request", impossible);
        }
    }

    /**
     * Someone else's conversation answers exactly as one that does not exist. A different answer for each would make a
     * conversation id a way to find out whose threads are there.
     */
    private TutorConversation requireOwnConversation(UUID conversationId, UUID userId) {
        return conversationRepo.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("TUTOR_CONVERSATION_NOT_FOUND",
                        "No conversation with id %s".formatted(conversationId)));
    }

    /**
     * Checked before the question is stored, unlike speaking's, which is checked at submission.
     * <p>
     * The difference is what the learner has already spent. A recording represents effort already made, so refusing it
     * late is refusing work they did; typing a question again costs a moment, and refusing before the thread grows a
     * turn that will never be answered is the kinder failure.
     */
    private void requireQuotaRemaining(UUID userId) {
        // Counted by the queue across every feature. Counting only tutor messages here is what let speaking and the
        // tutor each spend the whole budget.
        if (!aiJobQueue.hasDailyAllowance(userId)) {
            throw new ConflictException("TUTOR_DAILY_LIMIT_REACHED",
                    "You have reached today's limit of %d AI requests".formatted(aiJobQueue.dailyRequestLimit()));
        }
    }
}
